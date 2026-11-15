package com.example.backend.task.command;

/** @param assigneeId user id of a workspace member, or null to unassign */
public record AssignTaskCommand(Long workspaceId, Long actorId, Long id, Long assigneeId) {
}
