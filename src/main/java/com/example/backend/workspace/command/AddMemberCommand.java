package com.example.backend.workspace.command;

/** Adds someone by email; an unknown email becomes a pending account that registering claims. */
public record AddMemberCommand(Long workspaceId, Long actorId, String email) {
}
