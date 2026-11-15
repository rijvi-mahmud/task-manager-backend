package com.example.backend.task.query;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.example.backend.task.domain.Task;
import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;

/**
 * Read-side repository. Extends the bare Repository marker so it exposes no
 * save/delete methods, and projects straight into read models.
 *
 * <p>One query per sort order: each ORDER BY is fixed and index-backed (V6),
 * and none of it is built from client input.
 */
public interface TaskReadRepository extends Repository<Task, Long> {

    String VIEW = """
            select new com.example.backend.task.query.TaskView(
                t.id, t.title, t.description, t.status, t.priority, t.dueDate, a.id, a.displayName,
                case when t.dueDate < :today and t.status <> com.example.backend.task.domain.TaskStatus.DONE
                     then true else false end,
                t.version,
                c.id, c.displayName,
                t.createdAt, t.updatedAt)
            from Task t
            left join User a on a.id = t.assigneeId
            left join User c on c.id = t.createdBy
            """;

    /** Always within one workspace. Null criteria mean "any"; a left join keeps unassigned tasks. */
    String WHERE = """
            where t.workspaceId = :workspaceId
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:assigneeId is null or t.assigneeId = :assigneeId)
              and (:unassigned = false or t.assigneeId is null)
              and (:dueFrom is null or t.dueDate >= :dueFrom)
              and (:dueTo is null or t.dueDate <= :dueTo)
              and (:pattern is null
                   or lower(t.title) like :pattern escape '!'
                   or lower(coalesce(t.description, '')) like :pattern escape '!')
            """;

    String COUNT = "select count(t) from Task t ";

    @Query(value = VIEW + WHERE + "order by t.createdAt desc, t.id desc", countQuery = COUNT + WHERE)
    Page<TaskView> findNewest(@Param("workspaceId") Long workspaceId, @Param("status") TaskStatus status, @Param("priority") TaskPriority priority,
                              @Param("assigneeId") Long assigneeId, @Param("unassigned") boolean unassigned,
                              @Param("pattern") String pattern, @Param("dueFrom") LocalDate dueFrom,
                              @Param("dueTo") LocalDate dueTo, @Param("today") LocalDate today, Pageable page);

    @Query(value = VIEW + WHERE + "order by t.dueDate asc nulls last, t.id desc", countQuery = COUNT + WHERE)
    Page<TaskView> findByDueDate(@Param("workspaceId") Long workspaceId, @Param("status") TaskStatus status, @Param("priority") TaskPriority priority,
                                 @Param("assigneeId") Long assigneeId, @Param("unassigned") boolean unassigned,
                                 @Param("pattern") String pattern, @Param("dueFrom") LocalDate dueFrom,
                              @Param("dueTo") LocalDate dueTo, @Param("today") LocalDate today, Pageable page);

    @Query(value = VIEW + WHERE + """
            order by case t.priority
                         when com.example.backend.task.domain.TaskPriority.HIGH then 0
                         when com.example.backend.task.domain.TaskPriority.MEDIUM then 1
                         else 2 end,
                     t.createdAt desc, t.id desc
            """, countQuery = COUNT + WHERE)
    Page<TaskView> findByPriority(@Param("workspaceId") Long workspaceId, @Param("status") TaskStatus status, @Param("priority") TaskPriority priority,
                                  @Param("assigneeId") Long assigneeId, @Param("unassigned") boolean unassigned,
                                  @Param("pattern") String pattern, @Param("dueFrom") LocalDate dueFrom,
                              @Param("dueTo") LocalDate dueTo, @Param("today") LocalDate today, Pageable page);

    @Query(VIEW + "where t.workspaceId = :workspaceId and t.id = :id")
    Optional<TaskView> findViewById(@Param("workspaceId") Long workspaceId, @Param("id") Long id,
                                    @Param("today") LocalDate today);

    @Query("""
            select new com.example.backend.task.query.TaskStatsView(
                count(t),
                coalesce(sum(case when t.status = com.example.backend.task.domain.TaskStatus.TODO then 1 else 0 end), 0),
                coalesce(sum(case when t.status = com.example.backend.task.domain.TaskStatus.IN_PROGRESS then 1 else 0 end), 0),
                coalesce(sum(case when t.status = com.example.backend.task.domain.TaskStatus.DONE then 1 else 0 end), 0),
                coalesce(sum(case when t.dueDate < :today
                                   and t.status <> com.example.backend.task.domain.TaskStatus.DONE
                                  then 1 else 0 end), 0))
            from Task t
            where t.workspaceId = :workspaceId
            """)
    TaskStatsView stats(@Param("workspaceId") Long workspaceId, @Param("today") LocalDate today);
}
