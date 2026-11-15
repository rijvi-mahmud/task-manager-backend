package com.example.backend.workspace.command;

public record RenameWorkspaceCommand(Long workspaceId, Long actorId, String name) {
}
