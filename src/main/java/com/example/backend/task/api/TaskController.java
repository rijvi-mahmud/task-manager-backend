package com.example.backend.task.api;

import java.net.URI;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.example.backend.common.api.ApiVersions;
import com.example.backend.common.api.PageResponse;
import com.example.backend.identity.security.AuthenticatedUser;
import com.example.backend.task.command.AssignTaskCommand;
import com.example.backend.task.command.ChangeTaskStatusCommand;
import com.example.backend.task.command.CreateTaskCommand;
import com.example.backend.task.command.DeleteTaskCommand;
import com.example.backend.task.command.TaskCommandService;
import com.example.backend.task.command.UpdateTaskCommand;
import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;
import com.example.backend.task.query.TaskQueryService;
import com.example.backend.task.query.TaskSearch;
import com.example.backend.task.query.TaskSort;
import com.example.backend.task.query.TaskStatsView;
import com.example.backend.task.query.TaskView;


/**
 * Maps HTTP to commands (writes) and queries (reads). Write endpoints run the
 * command, then read the result back through the query side.
 *
 * <p>The contract, including parameter constraints, lives on {@link TaskApi}:
 * Bean Validation forbids an overriding method from redefining them.
 */
@RestController
@RequestMapping(path = ApiVersions.BASE + "/workspaces/{workspaceId}/tasks", version = ApiVersions.V1)
public class TaskController implements TaskApi {

    private final TaskCommandService commands;
    private final TaskQueryService queries;

    public TaskController(TaskCommandService commands, TaskQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @Override
    @GetMapping
    public PageResponse<TaskView> list(@PathVariable Long workspaceId,
                                       @RequestParam(required = false) TaskStatus status,
                                       @RequestParam(required = false) TaskPriority priority,
                                       @RequestParam(required = false) Long assigneeId,
                                       @RequestParam(defaultValue = "false") boolean unassigned,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
                                       @RequestParam(defaultValue = "NEWEST") TaskSort sort,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "25") int size,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        TaskSearch search = new TaskSearch(status, priority, assigneeId, unassigned, q, dueFrom, dueTo);
        return PageResponse.of(queries.find(workspaceId, user.id(), search, sort, page, size));
    }

    @Override
    @GetMapping("/stats")
    public TaskStatsView stats(@PathVariable Long workspaceId, @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.stats(workspaceId, user.id());
    }

    @Override
    @GetMapping("/{id}")
    public TaskView get(@PathVariable Long workspaceId, @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return queries.findById(workspaceId, user.id(), id);
    }

    @Override
    @PostMapping
    public ResponseEntity<TaskView> create(@PathVariable Long workspaceId, @RequestBody CreateTaskRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        Long id = commands.handle(new CreateTaskCommand(workspaceId, user.id(),
                request.title(), request.description(), request.status(), request.priority(),
                request.dueDate(), request.assigneeId()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(id).toUri();
        return ResponseEntity.created(location).body(queries.findById(workspaceId, user.id(), id));
    }

    @Override
    @PutMapping("/{id}")
    public TaskView update(@PathVariable Long workspaceId, @PathVariable Long id, @RequestBody UpdateTaskRequest request,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        commands.handle(new UpdateTaskCommand(workspaceId, user.id(),
                id, request.version(), request.title(), request.description(), request.status(), request.priority(),
                request.dueDate(), request.assigneeId()));
        return queries.findById(workspaceId, user.id(), id);
    }

    @Override
    @PatchMapping("/{id}/status")
    public TaskView changeStatus(@PathVariable Long workspaceId, @PathVariable Long id, @RequestBody ChangeTaskStatusRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        commands.handle(new ChangeTaskStatusCommand(workspaceId, user.id(), id, request.status()));
        return queries.findById(workspaceId, user.id(), id);
    }

    @Override
    @PatchMapping("/{id}/assignee")
    public TaskView assign(@PathVariable Long workspaceId, @PathVariable Long id, @RequestBody AssignTaskRequest request,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        commands.handle(new AssignTaskCommand(workspaceId, user.id(), id, request.assigneeId()));
        return queries.findById(workspaceId, user.id(), id);
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long workspaceId, @PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        commands.handle(new DeleteTaskCommand(workspaceId, user.id(), id));
    }
}
