package com.example.backend.workspace.domain;

/**
 * Published (synchronously, inside the removing transaction) just before a
 * membership is deleted, so other modules can drop references to the user in
 * that workspace without the workspace module depending on them.
 *
 * @param actorId the user who removed the member (the member themselves when leaving)
 */
public record MemberRemovedEvent(Long workspaceId, Long userId, Long actorId) {
}
