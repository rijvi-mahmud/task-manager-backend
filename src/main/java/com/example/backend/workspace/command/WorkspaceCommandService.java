package com.example.backend.workspace.command;

import org.springframework.context.ApplicationEventPublisher;
import com.example.backend.common.observability.AuditEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.common.exception.ConflictException;
import com.example.backend.common.exception.ForbiddenException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.identity.command.UserCommandService;
import com.example.backend.workspace.WorkspaceAccess;
import com.example.backend.workspace.domain.MemberRemovedEvent;
import com.example.backend.workspace.domain.Workspace;
import com.example.backend.workspace.domain.WorkspaceMember;
import com.example.backend.workspace.domain.WorkspaceMemberRepository;
import com.example.backend.workspace.domain.WorkspaceRepository;
import com.example.backend.workspace.domain.WorkspaceRole;

/**
 * Workspace and membership changes. Every command names its actor and is
 * authorized here, in the application layer, not in the controller.
 */
@Service
@Transactional
public class WorkspaceCommandService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final WorkspaceAccess access;
    private final UserCommandService users;
    private final ApplicationEventPublisher events;

    public WorkspaceCommandService(WorkspaceRepository workspaces, WorkspaceMemberRepository members,
                                   WorkspaceAccess access, UserCommandService users, ApplicationEventPublisher events) {
        this.workspaces = workspaces;
        this.members = members;
        this.access = access;
        this.users = users;
        this.events = events;
    }

    /** @return id of the new workspace, owned by the actor */
    public Long handle(CreateWorkspaceCommand command) {
        Workspace workspace = workspaces.save(new Workspace(command.name()));
        members.save(new WorkspaceMember(workspace.getId(), command.actorId(), WorkspaceRole.OWNER));
        events.publishEvent(new AuditEvent("workspace.created", command.actorId(), workspace.getId(), workspace.getId()));
        return workspace.getId();
    }

    public void handle(RenameWorkspaceCommand command) {
        access.requireOwner(command.workspaceId(), command.actorId(), "rename the workspace");
        load(command.workspaceId()).rename(command.name());
        events.publishEvent(new AuditEvent("workspace.renamed", command.actorId(), command.workspaceId(), command.workspaceId()));
    }

    /** Deletes the workspace with its tasks and memberships (database cascades). */
    public void handle(DeleteWorkspaceCommand command) {
        access.requireOwner(command.workspaceId(), command.actorId(), "delete the workspace");
        workspaces.delete(load(command.workspaceId()));
        // Run the delete (and the database's cascades) now, so later reads in the
        // same transaction cannot still see the workspace's memberships or tasks.
        workspaces.flush();
        events.publishEvent(new AuditEvent("workspace.deleted", command.actorId(), command.workspaceId(), command.workspaceId()));
    }

    /** @return id of the added user */
    public Long handle(AddMemberCommand command) {
        access.requireOwner(command.workspaceId(), command.actorId(), "add members");
        Long userId = users.findOrInvite(command.email());
        if (members.existsByWorkspaceIdAndUserId(command.workspaceId(), userId)) {
            throw new ConflictException("This person is already a member of the workspace");
        }
        members.save(new WorkspaceMember(command.workspaceId(), userId, WorkspaceRole.MEMBER));
        events.publishEvent(new AuditEvent("workspace.member_added", command.actorId(), command.workspaceId(), userId));
        return userId;
    }

    /**
     * Owners remove anyone; members can only remove themselves (leave). A
     * workspace always keeps at least one owner.
     */
    public void handle(RemoveMemberCommand command) {
        boolean leaving = command.actorId().equals(command.userId());
        WorkspaceRole actorRole = access.requireMember(command.workspaceId(), command.actorId());
        if (!leaving && actorRole != WorkspaceRole.OWNER) {
            throw new ForbiddenException("Only workspace owners can remove members.");
        }
        workspaces.findByIdForUpdate(command.workspaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", command.workspaceId()));
        WorkspaceMember member = members.findForRemoval(command.workspaceId(), command.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Member", command.userId()));
        if (member.isOwner() && members.countByWorkspaceIdAndRole(command.workspaceId(), WorkspaceRole.OWNER) == 1) {
            throw new ConflictException("A workspace needs at least one owner. Delete the workspace instead.");
        }
        events.publishEvent(new MemberRemovedEvent(command.workspaceId(), command.userId(), command.actorId()));
        members.delete(member);
        events.publishEvent(new AuditEvent("workspace.member_removed", command.actorId(), command.workspaceId(), command.userId()));
    }

    private Workspace load(Long id) {
        return workspaces.findById(id).orElseThrow(() -> new ResourceNotFoundException("Workspace", id));
    }
}
