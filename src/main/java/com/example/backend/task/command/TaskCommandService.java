package com.example.backend.task.command;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import com.example.backend.common.observability.AuditEvent;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.exception.DomainValidationException;
import com.example.backend.common.exception.ForbiddenException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.task.domain.Task;
import com.example.backend.task.domain.TaskRepository;
import com.example.backend.workspace.WorkspaceAccess;
import com.example.backend.workspace.domain.WorkspaceRole;

/**
 * Write side. Each method handles one command: authorize the actor, load the
 * aggregate within its workspace, apply a domain method, persist. Commands
 * change state and return at most an id; callers read results back through
 * the query side.
 */
@Service
@Transactional
public class TaskCommandService {

    private final ApplicationEventPublisher events;
    private final TaskRepository tasks;
    private final WorkspaceAccess access;

    public TaskCommandService(TaskRepository tasks, WorkspaceAccess access, ApplicationEventPublisher events) {
        this.events = events;
        this.tasks = tasks;
        this.access = access;
    }

    /** @return id of the created task */
    public Long handle(CreateTaskCommand command) {
        access.requireMember(command.workspaceId(), command.actorId());
        Task task = new Task(
                command.workspaceId(),
                command.actorId(),
                command.title(),
                command.description(),
                command.status(),
                command.priority(),
                command.dueDate());
        task.assignTo(requireAssignable(command.workspaceId(), command.assigneeId()));
        Long id = tasks.save(task).getId();
        events.publishEvent(new AuditEvent("task.created", command.actorId(), command.workspaceId(), id));
        return id;
    }

    public void handle(UpdateTaskCommand command) {
        Task task = load(command.workspaceId(), command.actorId(), command.id());
        task.verifyVersion(command.expectedVersion());
        task.updateDetails(command.title(), command.description(), command.priority(), command.dueDate());
        task.changeStatus(command.status());
        task.assignTo(requireAssignable(command.workspaceId(), command.assigneeId()));
        task.changedBy(command.actorId());
        events.publishEvent(new AuditEvent("task.updated", command.actorId(), command.workspaceId(), command.id()));
    }

    public void handle(ChangeTaskStatusCommand command) {
        Task task = load(command.workspaceId(), command.actorId(), command.id());
        task.changeStatus(command.status());
        task.changedBy(command.actorId());
        events.publishEvent(new AuditEvent("task.status_changed", command.actorId(), command.workspaceId(), command.id()));
    }

    public void handle(AssignTaskCommand command) {
        Task task = load(command.workspaceId(), command.actorId(), command.id());
        task.assignTo(requireAssignable(command.workspaceId(), command.assigneeId()));
        task.changedBy(command.actorId());
        events.publishEvent(new AuditEvent("task.assigned", command.actorId(), command.workspaceId(), command.id()));
    }

    /** Owners may delete any task; members only the tasks they created. */
    public void handle(DeleteTaskCommand command) {
        WorkspaceRole role = access.requireMember(command.workspaceId(), command.actorId());
        Task task = find(command.workspaceId(), command.id());
        if (role != WorkspaceRole.OWNER && !task.wasCreatedBy(command.actorId())) {
            throw new ForbiddenException("Only the task's creator or a workspace owner can delete it.");
        }
        tasks.delete(task);
        events.publishEvent(new AuditEvent("task.deleted", command.actorId(), command.workspaceId(), command.id()));
    }

    // Managed entities are flushed on commit, so update/status/assign need no explicit save().
    private Task load(Long workspaceId, Long actorId, Long id) {
        access.requireMember(workspaceId, actorId);
        return find(workspaceId, id);
    }

    private Task find(Long workspaceId, Long id) {
        return tasks.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    /** An assignee outside the workspace is a client error (400), not a missing task (404). */
    private Long requireAssignable(Long workspaceId, Long userId) {
        if (userId != null && !access.isMember(workspaceId, userId)) {
            throw new DomainValidationException("User " + userId + " is not a member of this workspace");
        }
        return userId;
    }
}
