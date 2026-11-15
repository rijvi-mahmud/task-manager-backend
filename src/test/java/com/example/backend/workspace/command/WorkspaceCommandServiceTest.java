package com.example.backend.workspace.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.example.backend.common.exception.ConflictException;
import com.example.backend.common.exception.ForbiddenException;
import com.example.backend.identity.command.UserCommandService;
import com.example.backend.workspace.WorkspaceAccess;
import com.example.backend.workspace.domain.MemberRemovedEvent;
import com.example.backend.workspace.domain.Workspace;
import com.example.backend.workspace.domain.WorkspaceMember;
import com.example.backend.workspace.domain.WorkspaceMemberRepository;
import com.example.backend.workspace.domain.WorkspaceRepository;
import com.example.backend.workspace.domain.WorkspaceRole;

class WorkspaceCommandServiceTest {

    static final Long WS = 10L;
    static final Long OWNER = 1L;
    static final Long MEMBER = 2L;

    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    WorkspaceAccess access = mock(WorkspaceAccess.class);
    UserCommandService users = mock(UserCommandService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    WorkspaceCommandService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceCommandService(workspaces, members, access, users, events);
        when(access.requireMember(WS, OWNER)).thenReturn(WorkspaceRole.OWNER);
        when(access.requireMember(WS, MEMBER)).thenReturn(WorkspaceRole.MEMBER);
        when(workspaces.findByIdForUpdate(WS)).thenReturn(Optional.of(new Workspace("Team")));
    }

    @Test
    void locksWorkspaceAndMembershipBeforeUnassigningAndRemoving() {
        WorkspaceMember member = new WorkspaceMember(WS, MEMBER, WorkspaceRole.MEMBER);
        when(members.findForRemoval(WS, MEMBER)).thenReturn(Optional.of(member));

        service.handle(new RemoveMemberCommand(WS, OWNER, MEMBER));

        var order = inOrder(workspaces, members, events);
        order.verify(workspaces).findByIdForUpdate(WS);
        order.verify(members).findForRemoval(WS, MEMBER);
        order.verify(events).publishEvent(new MemberRemovedEvent(WS, MEMBER, OWNER));
        order.verify(members).delete(member);
    }

    @Test
    void membersCanLeaveButCannotRemoveOthers() {
        when(members.findForRemoval(WS, MEMBER)).thenReturn(Optional.of(new WorkspaceMember(WS, MEMBER, WorkspaceRole.MEMBER)));
        service.handle(new RemoveMemberCommand(WS, MEMBER, MEMBER));

        assertThatThrownBy(() -> service.handle(new RemoveMemberCommand(WS, MEMBER, OWNER)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void theLastOwnerCannotLeave() {
        when(members.findForRemoval(WS, OWNER)).thenReturn(Optional.of(new WorkspaceMember(WS, OWNER, WorkspaceRole.OWNER)));
        when(members.countByWorkspaceIdAndRole(WS, WorkspaceRole.OWNER)).thenReturn(1L);

        assertThatThrownBy(() -> service.handle(new RemoveMemberCommand(WS, OWNER, OWNER)))
                .isInstanceOf(ConflictException.class);
        verify(events, never()).publishEvent(any());
        verify(members, never()).delete(any());
    }

    @Test
    void addingAnExistingMemberIsAConflict() {
        when(users.findOrInvite("dup@example.com")).thenReturn(MEMBER);
        when(members.existsByWorkspaceIdAndUserId(WS, MEMBER)).thenReturn(true);

        assertThatThrownBy(() -> service.handle(new AddMemberCommand(WS, OWNER, "dup@example.com")))
                .isInstanceOf(ConflictException.class);
        verify(members, never()).save(any());
    }

    @Test
    void ownerChecksRunBeforeAnyChange() {
        org.mockito.Mockito.doThrow(new ForbiddenException("no"))
                .when(access).requireOwner(WS, MEMBER, "add members");

        assertThatThrownBy(() -> service.handle(new AddMemberCommand(WS, MEMBER, "x@example.com")))
                .isInstanceOf(ForbiddenException.class);
        verify(users, never()).findOrInvite(any());
    }
}
