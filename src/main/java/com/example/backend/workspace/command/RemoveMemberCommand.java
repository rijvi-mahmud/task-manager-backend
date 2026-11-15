package com.example.backend.workspace.command;

/** Removing yourself means leaving the workspace. */
public record RemoveMemberCommand(Long workspaceId, Long actorId, Long userId) {
}
