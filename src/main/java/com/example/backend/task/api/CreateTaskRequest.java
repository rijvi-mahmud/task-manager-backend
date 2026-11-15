package com.example.backend.task.api;

import java.time.LocalDate;

import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A new task")
public record CreateTaskRequest(
        @Schema(description = "Trimmed before saving", example = "Write release notes")
        @NotBlank(message = "Title is required")
        @Size(max = 120, message = "Title must be at most 120 characters")
        String title,

        @Schema(description = "Optional; blank is stored as null", types = {"string", "null"}, example = "Cover the new board filters")
        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotNull(message = "Status is required")
        TaskStatus status,

        @NotNull(message = "Priority is required")
        TaskPriority priority,

        @Schema(description = "Calendar day, no time zone; years 0001 through 9999", types = {"string", "null"}, format = "date", example = "2026-12-01")
        LocalDate dueDate,

        @Schema(description = "User to assign (a workspace member); null leaves the task unassigned", types = {"integer", "null"}, format = "int64", example = "1")
        Long assigneeId) {
}
