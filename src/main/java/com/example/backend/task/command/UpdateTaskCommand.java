package com.example.backend.task.command;

import java.time.LocalDate;

import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;

public record UpdateTaskCommand(
        Long workspaceId,
        Long actorId,
        Long id,
        long expectedVersion,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        Long assigneeId) {
}
