package com.example.backend.workspace.query;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.example.backend.workspace.domain.Workspace;

/** Read side: projections only, always from the current user's point of view. */
public interface WorkspaceReadRepository extends Repository<Workspace, Long> {

    String VIEW = """
            select new com.example.backend.workspace.query.WorkspaceView(
                w.id, w.name, m.role,
                (select count(other) from WorkspaceMember other where other.workspaceId = w.id))
            from Workspace w join WorkspaceMember m on m.workspaceId = w.id
            where m.userId = :userId
            """;

    @Query(VIEW + "order by lower(w.name), w.id")
    List<WorkspaceView> findForUser(@Param("userId") Long userId);

    @Query(VIEW + "and w.id = :workspaceId")
    Optional<WorkspaceView> findForUser(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    @Query("""
            select new com.example.backend.workspace.query.WorkspaceMemberView(
                u.id, u.displayName, u.email, m.role,
                case when u.passwordHash is null then true else false end)
            from WorkspaceMember m join User u on u.id = m.userId
            where m.workspaceId = :workspaceId
            order by lower(u.displayName), u.id
            """)
    List<WorkspaceMemberView> findMembers(@Param("workspaceId") Long workspaceId);

    @Query("""
            select new com.example.backend.workspace.query.WorkspaceMemberView(
                u.id, u.displayName, u.email, m.role,
                case when u.passwordHash is null then true else false end)
            from WorkspaceMember m join User u on u.id = m.userId
            where m.workspaceId = :workspaceId and m.userId = :userId
            """)
    Optional<WorkspaceMemberView> findMember(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);
}
