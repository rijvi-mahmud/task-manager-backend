package com.example.backend.task.query;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.workspace.WorkspaceAccess;

/** Read side. Never changes state; only workspace members can read a workspace's tasks. */
@Service
@Transactional(readOnly = true)
public class TaskQueryService {
    private static final Logger log = LoggerFactory.getLogger(TaskQueryService.class);

    private final TaskReadRepository repository;
    private final WorkspaceAccess access;
    private final Clock clock;

    public TaskQueryService(TaskReadRepository repository, WorkspaceAccess access, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.clock = clock;
    }

    public Page<TaskView> find(Long workspaceId, Long actorId, TaskSearch search, TaskSort sort, int page, int size) {
        log.debug("Listing tasks workspaceId={} actorId={} page={} size={} sort={}", workspaceId, actorId, page, size, sort);
        access.requireMember(workspaceId, actorId);
        PageRequest pageable = PageRequest.of(page, size);
        String pattern = search.likePattern();
        LocalDate today = today();
        return switch (sort) {
            case NEWEST -> repository.findNewest(workspaceId, search.status(), search.priority(), search.assigneeId(),
                    search.unassigned(), pattern, search.dueFrom(), search.dueTo(), today, pageable);
            case DUE_DATE -> repository.findByDueDate(workspaceId, search.status(), search.priority(), search.assigneeId(),
                    search.unassigned(), pattern, search.dueFrom(), search.dueTo(), today, pageable);
            case PRIORITY -> repository.findByPriority(workspaceId, search.status(), search.priority(), search.assigneeId(),
                    search.unassigned(), pattern, search.dueFrom(), search.dueTo(), today, pageable);
        };
    }

    public TaskView findById(Long workspaceId, Long actorId, Long id) {
        log.debug("Reading task workspaceId={} actorId={} taskId={}", workspaceId, actorId, id);
        access.requireMember(workspaceId, actorId);
        return repository.findViewById(workspaceId, id, today())
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    public TaskStatsView stats(Long workspaceId, Long actorId) {
        log.debug("Reading task statistics workspaceId={} actorId={}", workspaceId, actorId);
        access.requireMember(workspaceId, actorId);
        return repository.stats(workspaceId, today());
    }

    /** "Today" in the business time zone carried by the Clock (see TimeConfig). */
    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
