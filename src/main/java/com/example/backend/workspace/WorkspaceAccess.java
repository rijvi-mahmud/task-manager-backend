package com.example.backend.workspace;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.exception.ForbiddenException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.workspace.domain.WorkspaceMemberRepository;
import com.example.backend.workspace.domain.WorkspaceRole;

/**
 * The one place that decides who may touch a workspace; other modules call
 * it before reading or writing workspace data.
 *
 * <p>Non-members get 404, not 403: an outsider cannot even confirm that a
 * workspace (or a task in it) exists.
 */
@Component
@Transactional(readOnly = true)
public class WorkspaceAccess {

    private final WorkspaceMemberRepository members;

    public WorkspaceAccess(WorkspaceMemberRepository members) {
        this.members = members;
    }

    /** @return the user's role in the workspace */
    public WorkspaceRole requireMember(Long workspaceId, Long userId) {
        return members.findRole(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    public void requireOwner(Long workspaceId, Long userId, String action) {
        if (requireMember(workspaceId, userId) != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Only workspace owners can " + action + ".");
        }
    }

    public boolean isMember(Long workspaceId, Long userId) {
        return members.existsByWorkspaceIdAndUserId(workspaceId, userId);
    }
}
