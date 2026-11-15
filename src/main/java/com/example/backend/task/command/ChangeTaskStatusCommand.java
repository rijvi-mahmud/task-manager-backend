package com.example.backend.task.command;

import com.example.backend.task.domain.TaskStatus;

public record ChangeTaskStatusCommand(Long workspaceId, Long actorId, Long id, TaskStatus status) {
}
