package com.example.backend.task.api;

import org.springframework.http.ResponseEntity;
import java.time.LocalDate;

import com.example.backend.common.api.ApiTags;
import com.example.backend.common.api.PageResponse;
import com.example.backend.common.api.ProblemResponses;
import com.example.backend.identity.security.AuthenticatedUser;
import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;
import com.example.backend.task.query.TaskSort;
import com.example.backend.task.query.TaskStatsView;
import com.example.backend.task.query.TaskView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * OpenAPI contract for the task endpoints. Kept apart from {@link TaskController}
 * so the controller shows only routing and command/query wiring.
 */
@Tag(name = ApiTags.TASKS)
public interface TaskApi {

    int MAX_PAGE_SIZE = 100;

    @Operation(operationId = "listTasks", summary = "List tasks, one page at a time",
            description = "All filters are optional and combine with AND. Every sort ends with a unique key, "
                    + "so pages are stable. The board requests one page per status column.")
    @ApiResponse(responseCode = "200", description = "One page of matching tasks")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    PageResponse<TaskView> list(
            @Parameter(description = "Workspace id") Long workspaceId,
            @Parameter(description = "Only tasks in this status") TaskStatus status,
            @Parameter(description = "Only tasks with this priority") TaskPriority priority,
            @Parameter(description = "Only tasks assigned to this user") Long assigneeId,
            @Parameter(description = "Only tasks with no assignee. Cannot be combined with `assigneeId`.") boolean unassigned,
            @Parameter(description = "Case-insensitive text to find in the title or description")
            @Size(max = 120, message = "Search text must be at most 120 characters") String q,
            @Parameter(description = "Inclusive lower due-date bound (ISO yyyy-MM-dd, years 0001-9999). Undated tasks are excluded when a bound is set.") LocalDate dueFrom,
            @Parameter(description = "Inclusive upper due-date bound (ISO yyyy-MM-dd, years 0001-9999). May be used without dueFrom.") LocalDate dueTo,
            @Parameter(description = "Order of results") TaskSort sort,
            @Parameter(description = "Zero-based page index")
            @Min(value = 0, message = "Page must be 0 or more") int page,
            @Parameter(description = "Page size")
            @Min(value = 1, message = "Size must be at least 1")
            @Max(value = MAX_PAGE_SIZE, message = "Size must be at most 100") int size,
            @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "getTaskStats", summary = "Workspace task counts",
            description = "Totals per status and the number of overdue tasks. Not affected by list filters.")
    @ApiResponse(responseCode = "200", description = "Counts")
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    TaskStatsView stats(@Parameter(description = "Workspace id") Long workspaceId, @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "getTask", summary = "Get a task")
    @ApiResponse(responseCode = "200", description = "The task")
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    TaskView get(@Parameter(description = "Workspace id") Long workspaceId, @Parameter(description = "Task id") Long id, @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "createTask", summary = "Create a task")
    @ApiResponse(responseCode = "201", description = "Task created",
            headers = @Header(name = "Location", description = "URI of the new task",
                    schema = @Schema(type = "string", format = "uri")),
            content = @Content(schema = @Schema(implementation = TaskView.class)))
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    ResponseEntity<TaskView> create(@Parameter(description = "Workspace id") Long workspaceId, @Valid CreateTaskRequest request, @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "updateTask", summary = "Replace a task's details",
            description = "Optimistic locking: send the `version` you last read. "
                    + "If the task changed since, nothing is written and `409` is returned.")
    @ApiResponse(responseCode = "200", description = "The updated task, with its new version")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    @ApiResponse(responseCode = "409", ref = ProblemResponses.CONFLICT_REF)
    TaskView update(@Parameter(description = "Workspace id") Long workspaceId, @Parameter(description = "Task id") Long id, @Valid UpdateTaskRequest request,
                    @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "changeTaskStatus", summary = "Move a task to another status",
            description = "Used by the Kanban board's drag and drop. Takes no `version`: a single-field "
                    + "move is last-write-wins by design, so dragging a card never fails because someone "
                    + "edited its text. Simultaneous commits on the same task still return `409`.")
    @ApiResponse(responseCode = "200", description = "The updated task")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    @ApiResponse(responseCode = "409", ref = ProblemResponses.CONFLICT_REF)
    TaskView changeStatus(@Parameter(description = "Workspace id") Long workspaceId, @Parameter(description = "Task id") Long id,
                          @Valid ChangeTaskStatusRequest request, @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "assignTask", summary = "Assign or unassign a task",
            description = "`assigneeId: null` (or omitted) unassigns. A user who is not a member of the workspace returns `400`. "
                    + "Like status changes, last-write-wins without a `version`.")
    @ApiResponse(responseCode = "200", description = "The updated task")
    @ApiResponse(responseCode = "400", ref = ProblemResponses.BAD_REQUEST_REF)
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    @ApiResponse(responseCode = "409", ref = ProblemResponses.CONFLICT_REF)
    TaskView assign(@Parameter(description = "Workspace id") Long workspaceId, @Parameter(description = "Task id") Long id, AssignTaskRequest request,
                    @Parameter(hidden = true) AuthenticatedUser user);

    @Operation(operationId = "deleteTask", summary = "Delete a task",
            description = "Allowed for the task's creator and workspace owners; others get `403`.")
    @ApiResponse(responseCode = "204", description = "Task deleted")
    @ApiResponse(responseCode = "404", ref = ProblemResponses.NOT_FOUND_REF)
    void delete(@Parameter(description = "Workspace id") Long workspaceId, @Parameter(description = "Task id") Long id, @Parameter(hidden = true) AuthenticatedUser user);
}
