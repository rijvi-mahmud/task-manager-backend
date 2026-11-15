package com.example.backend.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.TestcontainersConfiguration;
import com.example.backend.common.exception.DomainValidationException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.identity.command.UserCommandService;
import com.example.backend.support.Accounts;
import com.example.backend.support.Accounts.TestUser;
import com.example.backend.task.command.AssignTaskCommand;
import com.example.backend.task.command.ChangeTaskStatusCommand;
import com.example.backend.task.command.CreateTaskCommand;
import com.example.backend.task.command.TaskCommandService;
import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;
import com.example.backend.task.query.TaskQueryService;
import com.example.backend.task.query.TaskSearch;
import com.example.backend.task.query.TaskSort;
import com.example.backend.task.query.TaskStatsView;
import com.example.backend.task.query.TaskView;
import com.example.backend.workspace.command.RemoveMemberCommand;
import com.example.backend.workspace.command.WorkspaceCommandService;

/**
 * Commands write through the entity; queries read projections. Runs on
 * PostgreSQL (Testcontainers) with a fixed Clock. Each test gets its own
 * users and workspace, so data from other tests never leaks into assertions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class TaskCqrsIntegrationTest {

    static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    static final Instant NOW = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired TaskCommandService commands;
    @Autowired TaskQueryService queries;
    @Autowired UserCommandService userCommands;
    @Autowired WorkspaceCommandService workspaceCommands;

    TestUser owner;
    TestUser member;
    Long ws;

    @BeforeEach
    void setUp() {
        Accounts accounts = new Accounts(userCommands, workspaceCommands);
        owner = accounts.user("Owner");
        member = accounts.user("Member");
        ws = accounts.workspace(owner, member);
    }

    @Test
    void overdueComputedByReadModel() {
        Long late = create("Late", TaskStatus.TODO, TaskPriority.HIGH, TODAY.minusDays(1));
        Long today = create("Today", TaskStatus.TODO, TaskPriority.LOW, TODAY);
        Long noDate = create("No date", TaskStatus.TODO, TaskPriority.LOW, null);

        assertThat(view(late).overdue()).isTrue();
        assertThat(view(today).overdue()).isFalse();
        assertThat(view(noDate).overdue()).isFalse();

        commands.handle(new ChangeTaskStatusCommand(ws, owner.id(), late, TaskStatus.DONE));
        TaskView done = view(late);
        assertThat(done.status()).isEqualTo(TaskStatus.DONE);
        assertThat(done.overdue()).isFalse();
    }

    @Test
    void timestampsComeFromTheApplicationClockAndAuthorsAreRecorded() {
        Long id = create("Stamped", TaskStatus.TODO, TaskPriority.LOW, null);
        TaskView view = view(id);
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(view.updatedAt()).isEqualTo(NOW);
        assertThat(view.createdById()).isEqualTo(owner.id());
        assertThat(view.createdByName()).isEqualTo("Owner");
    }

    @Test
    void tasksAreInvisibleOutsideTheirWorkspace() {
        Long id = create("Private", TaskStatus.TODO, TaskPriority.LOW, null);
        Long otherWs = new Accounts(userCommands, workspaceCommands).workspace(member);

        // Same task id, wrong workspace: not found, even for someone who is a member of both.
        assertThatThrownBy(() -> queries.findById(otherWs, member.id(), id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> commands.handle(new ChangeTaskStatusCommand(otherWs, member.id(), id, TaskStatus.DONE)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(queries.find(otherWs, member.id(), TaskSearch.any(), TaskSort.NEWEST, 0, 10).getTotalElements()).isZero();
    }

    @Test
    void onlyWorkspaceMembersCanBeAssigned() {
        Long id = create("Assign", TaskStatus.TODO, TaskPriority.LOW, null);
        TestUser outsider = new Accounts(userCommands, workspaceCommands).user("Outsider");

        commands.handle(new AssignTaskCommand(ws, owner.id(), id, member.id()));
        assertThat(view(id).assigneeName()).isEqualTo("Member");

        assertThatThrownBy(() -> commands.handle(new AssignTaskCommand(ws, owner.id(), id, outsider.id())))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void removingAMemberUnassignsTheirTasksAndBumpsTheVersion() {
        Long id = create("Handover", TaskStatus.TODO, TaskPriority.LOW, null);
        commands.handle(new AssignTaskCommand(ws, owner.id(), id, member.id()));
        long version = view(id).version();

        workspaceCommands.handle(new RemoveMemberCommand(ws, owner.id(), member.id()));

        TaskView after = view(id);
        assertThat(after.assigneeId()).isNull();
        assertThat(after.version()).isEqualTo(version + 1);
    }

    @Test
    void filtersByStatusAndPriority() {
        create("filt A", TaskStatus.TODO, TaskPriority.HIGH, null);
        create("filt B", TaskStatus.DONE, TaskPriority.HIGH, null);
        create("filt C", TaskStatus.TODO, TaskPriority.LOW, null);

        assertThat(titles(new TaskSearch(TaskStatus.TODO, TaskPriority.HIGH, null, false, "filt"), TaskSort.NEWEST))
                .containsExactly("filt A");
        assertThat(titles(new TaskSearch(null, TaskPriority.HIGH, null, false, "filt"), TaskSort.NEWEST))
                .containsExactlyInAnyOrder("filt A", "filt B");
    }

    @Test
    void searchIsCaseInsensitiveCoversDescriptionAndTreatsWildcardsLiterally() {
        create("wild 50% done", TaskStatus.TODO, TaskPriority.LOW, null);
        create("wild 500 done", TaskStatus.TODO, TaskPriority.LOW, null);
        create("wild_x", TaskStatus.TODO, TaskPriority.LOW, null);
        commands.handle(new CreateTaskCommand(ws, owner.id(), "other", "Mentions WILDEBEEST", TaskStatus.TODO,
                TaskPriority.LOW, null, null));

        assertThat(titles(new TaskSearch(null, null, null, false, "50%"), TaskSort.NEWEST)).containsExactly("wild 50% done");
        assertThat(titles(new TaskSearch(null, null, null, false, "D_X"), TaskSort.NEWEST)).containsExactly("wild_x");
        assertThat(titles(new TaskSearch(null, null, null, false, "wildebeest"), TaskSort.NEWEST)).containsExactly("other");
    }

    @Test
    void sortsByPriorityThenNewest() {
        create("prio low", TaskStatus.TODO, TaskPriority.LOW, null);
        create("prio high", TaskStatus.TODO, TaskPriority.HIGH, null);
        create("prio medium", TaskStatus.TODO, TaskPriority.MEDIUM, null);

        assertThat(titles(new TaskSearch(null, null, null, false, "prio"), TaskSort.PRIORITY))
                .containsExactly("prio high", "prio medium", "prio low");
    }

    @Test
    void sortsByDueDateWithUndatedTasksLast() {
        create("due none", TaskStatus.TODO, TaskPriority.LOW, null);
        create("due later", TaskStatus.TODO, TaskPriority.LOW, TODAY.plusDays(5));
        create("due sooner", TaskStatus.TODO, TaskPriority.LOW, TODAY.plusDays(1));

        assertThat(titles(new TaskSearch(null, null, null, false, "due "), TaskSort.DUE_DATE))
                .containsExactly("due sooner", "due later", "due none");
    }

    @Test
    void pagesAreStableAndComplete() {
        for (int i = 1; i <= 5; i++) {
            create("page " + i, TaskStatus.TODO, TaskPriority.LOW, null); // identical createdAt (fixed clock)
        }
        TaskSearch search = new TaskSearch(null, null, null, false, "page ");

        Page<TaskView> first = queries.find(ws, owner.id(), search, TaskSort.NEWEST, 0, 2);
        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);

        List<String> all = new ArrayList<>();
        for (int p = 0; p < 3; p++) {
            queries.find(ws, owner.id(), search, TaskSort.NEWEST, p, 2).forEach(t -> all.add(t.title()));
        }
        // Ties on createdAt are broken by id, so no task is skipped or repeated.
        assertThat(all).containsExactly("page 5", "page 4", "page 3", "page 2", "page 1");
    }

    @Test
    void unassignedFilterExcludesAssignedTasks() {
        Long assigned = create("una assigned", TaskStatus.TODO, TaskPriority.LOW, null);
        commands.handle(new AssignTaskCommand(ws, owner.id(), assigned, member.id()));
        create("una free", TaskStatus.TODO, TaskPriority.LOW, null);

        assertThat(titles(new TaskSearch(null, null, null, true, "una "), TaskSort.NEWEST)).containsExactly("una free");
        assertThat(titles(new TaskSearch(null, null, member.id(), false, "una "), TaskSort.NEWEST)).containsExactly("una assigned");
    }

    @Test
    void statsCountOnlyThisWorkspace() {
        create("stat 1", TaskStatus.TODO, TaskPriority.LOW, TODAY.minusDays(1)); // overdue
        create("stat 2", TaskStatus.IN_PROGRESS, TaskPriority.LOW, null);
        create("stat 3", TaskStatus.DONE, TaskPriority.LOW, TODAY.minusDays(1)); // done: not overdue
        Long otherWs = new Accounts(userCommands, workspaceCommands).workspace(owner);
        commands.handle(new CreateTaskCommand(otherWs, owner.id(), "elsewhere", null, TaskStatus.TODO, TaskPriority.LOW, null, null));

        TaskStatsView stats = queries.stats(ws, owner.id());
        assertThat(stats).isEqualTo(new TaskStatsView(3L, 1L, 1L, 1L, 1L));
    }

    private TaskView view(Long id) {
        return queries.findById(ws, owner.id(), id);
    }

    private List<String> titles(TaskSearch search, TaskSort sort) {
        return queries.find(ws, owner.id(), search, sort, 0, 100).map(TaskView::title).getContent();
    }

    private Long create(String title, TaskStatus status, TaskPriority priority, LocalDate due) {
        return commands.handle(new CreateTaskCommand(ws, owner.id(), title, null, status, priority, due, null));
    }
}
