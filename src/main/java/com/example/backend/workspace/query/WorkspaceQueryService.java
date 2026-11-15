package com.example.backend.workspace.query;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.workspace.WorkspaceAccess;

/** Read side. Callers only ever see workspaces they belong to. */
@Service
@Transactional(readOnly = true)
public class WorkspaceQueryService {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceQueryService.class);

    private final WorkspaceReadRepository repository;
    private final WorkspaceAccess access;

    public WorkspaceQueryService(WorkspaceReadRepository repository, WorkspaceAccess access) {
        this.repository = repository;
        this.access = access;
    }

    public List<WorkspaceView> findMine(Long userId) {
        log.debug("Listing workspaces userId={}", userId);
        return repository.findForUser(userId);
    }

    public WorkspaceView findById(Long workspaceId, Long userId) {
        log.debug("Reading workspace workspaceId={} userId={}", workspaceId, userId);
        return repository.findForUser(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }

    public List<WorkspaceMemberView> findMembers(Long workspaceId, Long userId) {
        log.debug("Listing members workspaceId={} userId={}", workspaceId, userId);
        access.requireMember(workspaceId, userId);
        return repository.findMembers(workspaceId);
    }

    public WorkspaceMemberView findMember(Long workspaceId, Long memberId, Long userId) {
        log.debug("Reading member workspaceId={} memberId={} userId={}", workspaceId, memberId, userId);
        access.requireMember(workspaceId, userId);
        return repository.findMember(workspaceId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
    }
}
