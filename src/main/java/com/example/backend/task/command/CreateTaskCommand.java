package com.example.backend.task.command;

import java.time.LocalDate;

import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;

/** @param actorId the signed-in user issuing the command */
public record CreateTaskCommand(
        Long workspaceId,
        Long actorId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        Long assigneeId) {
}
