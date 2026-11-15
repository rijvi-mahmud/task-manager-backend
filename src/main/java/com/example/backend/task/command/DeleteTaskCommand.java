package com.example.backend.task.command;

public record DeleteTaskCommand(Long workspaceId, Long actorId, Long id) {
}
