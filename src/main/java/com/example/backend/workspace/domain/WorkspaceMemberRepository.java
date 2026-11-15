package com.example.backend.workspace.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, WorkspaceMember.Key> {

    @Query("select m.role from WorkspaceMember m where m.workspaceId = :workspaceId and m.userId = :userId")
    Optional<WorkspaceRole> findRole(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);

    boolean existsByUserId(Long userId);

    long countByWorkspaceIdAndRole(Long workspaceId, WorkspaceRole role);

    /**
     * Locks the membership row before it is removed. FOR UPDATE (not the
     * weaker FOR NO KEY UPDATE) blocks the KEY SHARE lock a concurrent task
     * assignment takes for its foreign key check, so nobody can be assigned
     * between unassigning their tasks and removing them.
     */
    @Query(value = "select * from {h-schema}workspace_members where workspace_id = :workspaceId and user_id = :userId for update",
            nativeQuery = true)
    Optional<WorkspaceMember> findForRemoval(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);
}
