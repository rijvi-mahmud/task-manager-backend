package com.example.backend.task.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Assignment change for a task")
public record AssignTaskRequest(
        @Schema(description = "User to assign (a workspace member); null or omitted unassigns", types = {"integer", "null"},
                format = "int64", example = "2")
        Long assigneeId) {
}
