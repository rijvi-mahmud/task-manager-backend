package com.example.backend.task.api;

import com.example.backend.task.domain.TaskStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "New status for a task")
public record ChangeTaskStatusRequest(
        @NotNull(message = "Status is required")
        TaskStatus status) {
}
