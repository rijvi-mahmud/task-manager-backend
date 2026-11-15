package com.example.backend.task.query;

import java.time.Instant;
import java.time.LocalDate;

import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/** Read model returned to clients. Built directly by JPQL, never from a loaded entity. */
@Schema(name = "Task", description = "A task as shown on the board")
public record TaskView(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "42")
        Long id,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "Write release notes")
        String title,
        @Schema(requiredMode = RequiredMode.REQUIRED, types = {"string", "null"})
        String description,
        @Schema(requiredMode = RequiredMode.REQUIRED)
        TaskStatus status,
        @Schema(requiredMode = RequiredMode.REQUIRED)
        TaskPriority priority,
        @Schema(requiredMode = RequiredMode.REQUIRED, types = {"string", "null"}, format = "date", example = "2026-12-01")
        LocalDate dueDate,
        @Schema(requiredMode = RequiredMode.REQUIRED, types = {"integer", "null"}, format = "int64", description = "User id of the assignee", example = "1")
        Long assigneeId,
        @Schema(requiredMode = RequiredMode.REQUIRED, types = {"string", "null"}, example = "Ada Lovelace")
        String assigneeName,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "Past its due date and not done")
        Boolean overdue,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "Optimistic-locking version; send it back on update", example = "3")
        Long version,
        @Schema(requiredMode = RequiredMode.REQUIRED, types = {"integer", "null"}, format = "int64",
                description = "User who created the task; null for tasks created before accounts existed", example = "1")
        Long createdById,
        @Schema(requiredMode = RequiredMode.REQUIRED, types = {"string", "null"}, example = "Ada Lovelace")
        String createdByName,
        @Schema(requiredMode = RequiredMode.REQUIRED)
        Instant createdAt,
        @Schema(requiredMode = RequiredMode.REQUIRED)
        Instant updatedAt) {
}
