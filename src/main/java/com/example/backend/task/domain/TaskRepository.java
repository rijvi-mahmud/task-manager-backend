package com.example.backend.task.domain;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Write-side repository: loads and saves the Task aggregate. Reads go through TaskReadRepository. */
public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Always load within a workspace: a task id from another workspace is "not found". */
    Optional<Task> findByIdAndWorkspaceId(Long id, Long workspaceId);

    /**
     * Bulk unassign within one workspace. Bulk JPQL bypasses entity callbacks and @Version, so the
     * version, timestamp and editor are set explicitly to keep optimistic locking and auditing correct.
     * {@code now} comes from the application Clock, like the timestamps JPA auditing sets.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Task t
               set t.assigneeId = null, t.version = t.version + 1, t.updatedAt = :now, t.updatedBy = :actorId
             where t.workspaceId = :workspaceId and t.assigneeId = :userId
            """)
    int unassignAllIn(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId,
                      @Param("actorId") Long actorId, @Param("now") Instant now);
}
