package com.example.backend.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.backend.TestcontainersConfiguration;
import com.example.backend.workspace.domain.WorkspaceMemberRepository;

/**
 * Verifies the database itself enforces integrity and least privilege,
 * independently of the application layer (raw SQL, no JPA).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseSchemaTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PostgreSQLContainer postgres;
    @Autowired WorkspaceMemberRepository members;
    @Autowired PlatformTransactionManager transactions;

    Long ws;
    Long user;

    @BeforeEach
    void setUp() {
        ws = jdbc.queryForObject("insert into taskmanager.workspaces (name) values ('DB test') returning id", Long.class);
        // Unique: the non-transactional lock test commits what setUp inserts.
        user = newUser("member-" + System.nanoTime() + "@db.test");
        jdbc.update("insert into taskmanager.workspace_members (workspace_id, user_id, role) values (?, ?, 'MEMBER')", ws, user);
    }

    private Long newUser(String email) {
        return jdbc.queryForObject(
                "insert into taskmanager.users (email, display_name) values (?, 'U') returning id", Long.class, email);
    }

    private Long insertTask(Long assignee) {
        return jdbc.queryForObject("""
                insert into taskmanager.tasks (workspace_id, title, status, priority, assignee_id)
                values (?, 't', 'TODO', 'LOW', ?) returning id
                """, Long.class, ws, assignee);
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into taskmanager.tasks (workspace_id, title, status, priority) values (?, '   ', 'TODO', 'LOW')", ws))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_tasks_title_not_blank");
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into taskmanager.tasks (workspace_id, title, status, priority) values (?, 'x', 'ARCHIVED', 'LOW')", ws))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_tasks_status");
    }

    @Test
    void tasksMustBelongToAWorkspace() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into taskmanager.tasks (title, status, priority) values ('x', 'TODO', 'LOW')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // One failing statement per test: Postgres aborts the rest of a transaction after an error.
    @Test
    void rejectsNonNormalizedEmail() {
        assertThatThrownBy(() -> newUser("Upper@Example.com")).hasMessageContaining("ck_users_email_lowercase");
    }

    @Test
    void rejectsMalformedEmail() {
        assertThatThrownBy(() -> newUser("not-an-email")).hasMessageContaining("ck_users_email_format");
    }

    @Test
    void rejectsUnknownRole() {
        Long other = newUser("role@db.test");
        assertThatThrownBy(() -> jdbc.update(
                "insert into taskmanager.workspace_members (workspace_id, user_id, role) values (?, ?, 'ADMIN')", ws, other))
                .hasMessageContaining("ck_workspace_members_role");
    }

    @Test
    void timestampsDefaultToNow() {
        Long id = insertTask(null);
        Boolean set = jdbc.queryForObject(
                "select created_at is not null and updated_at is not null from taskmanager.tasks where id = ?",
                Boolean.class, id);
        assertThat(set).isTrue();
    }

    @Test
    void assigneeMustBeAMemberOfTheTasksWorkspace() {
        Long stranger = newUser("stranger@db.test");
        assertThatThrownBy(() -> insertTask(stranger))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_tasks_assignee_membership");
    }

    @Test
    void removingAMembershipUnassignsTasksButKeepsTheWorkspace() {
        Long taskId = insertTask(user);

        jdbc.update("delete from taskmanager.workspace_members where workspace_id = ? and user_id = ?", ws, user);

        assertThat(jdbc.queryForObject("select assignee_id from taskmanager.tasks where id = ?", Long.class, taskId)).isNull();
        assertThat(jdbc.queryForObject("select workspace_id from taskmanager.tasks where id = ?", Long.class, taskId)).isEqualTo(ws);
    }

    @Test
    void deletingAWorkspaceCascadesToTasksAndMemberships() {
        insertTask(user);
        jdbc.update("delete from taskmanager.workspaces where id = ?", ws);
        assertThat(jdbc.queryForObject("select count(*) from taskmanager.tasks where workspace_id = ?", Long.class, ws)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from taskmanager.workspace_members where workspace_id = ?", Long.class, ws)).isZero();
    }

    @Test
    void appRoleHasDataAccessButNoDdl() throws SQLException {
        try (Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(),
                TestcontainersConfiguration.APP_ROLE, TestcontainersConfiguration.APP_ROLE_PASSWORD);
             Statement st = conn.createStatement()) {

            for (String table : new String[] {"users", "workspaces", "workspace_members", "password_reset_tokens",
                    "spring_session", "spring_session_attributes"}) {
                assertThat(st.executeQuery("select count(*) from taskmanager." + table).next()).as(table).isTrue();
            }
            assertThatThrownBy(() -> st.execute("drop table taskmanager.tasks"))
                    .hasMessageContaining("must be owner");
            assertThatThrownBy(() -> st.execute("delete from taskmanager.flyway_schema_history"))
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void membershipRemovalLockBlocksConcurrentAssignments() throws SQLException {
        Long lockWs = jdbc.queryForObject("insert into taskmanager.workspaces (name) values ('Lock test') returning id", Long.class);
        Long lockUser = newUser("lock-" + System.nanoTime() + "@db.test");
        jdbc.update("insert into taskmanager.workspace_members (workspace_id, user_id, role) values (?, ?, 'MEMBER')", lockWs, lockUser);
        try (Connection contender = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
             Statement statement = contender.createStatement()) {
            statement.execute("set lock_timeout = '200ms'");
            String insert = "insert into taskmanager.tasks (workspace_id, title, status, priority, assignee_id) "
                    + "values (" + lockWs + ", 'Lock test', 'TODO', 'LOW', " + lockUser + ")";
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThat(members.findForRemoval(lockWs, lockUser)).isPresent();
                // A weaker FOR NO KEY UPDATE lock would allow this insert.
                assertThatThrownBy(() -> statement.execute(insert))
                        .isInstanceOf(SQLException.class)
                        .satisfies(ex -> assertThat(((SQLException) ex).getSQLState()).isEqualTo("55P03"));
            });
            assertThat(statement.executeUpdate(insert)).isEqualTo(1);
        } finally {
            jdbc.update("delete from taskmanager.workspaces where id = ?", lockWs);
            jdbc.update("delete from taskmanager.users where id = ?", lockUser);
        }
    }
}
