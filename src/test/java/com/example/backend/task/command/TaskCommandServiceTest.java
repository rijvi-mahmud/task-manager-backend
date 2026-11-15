package com.example.backend.task.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import com.example.backend.common.observability.AuditEvent;

import com.example.backend.common.exception.ConflictException;
import com.example.backend.common.exception.DomainValidationException;
import com.example.backend.common.exception.ForbiddenException;
import com.example.backend.common.exception.ResourceNotFoundException;
import com.example.backend.task.domain.Task;
import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskRepository;
import com.example.backend.task.domain.TaskStatus;
import com.example.backend.workspace.WorkspaceAccess;
import com.example.backend.workspace.domain.WorkspaceRole;

class TaskCommandServiceTest {

    private static final Long WS = 10L;
    private static final Long ACTOR = 1L;

    private TaskRepository repository;
    private WorkspaceAccess access;
    private TaskCommandService service;
    private ApplicationEventPublisher events;

    @BeforeEach
    void setUp() {
        repository = mock(TaskRepository.class);
        access = mock(WorkspaceAccess.class);
        when(access.requireMember(WS, ACTOR)).thenReturn(WorkspaceRole.MEMBER);
        events = mock(ApplicationEventPublisher.class);
        service = new TaskCommandService(repository, access, events);
    }

    private static Task task(Long createdBy) {
        return new Task(WS, createdBy, "Task", null, TaskStatus.TODO, TaskPriority.MEDIUM, null);
    }

    @Test
    void createBuildsTrimmedTaskAndSavesIt() {
        when(repository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        service.handle(new CreateTaskCommand(WS, ACTOR, "  Write report  ", " ", TaskStatus.TODO, TaskPriority.HIGH, null, null));

        ArgumentCaptor<Task> saved = ArgumentCaptor.forClass(Task.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Write report");
        assertThat(saved.getValue().getDescription()).isNull();
        assertThat(saved.getValue().getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(saved.getValue().getWorkspaceId()).isEqualTo(WS);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(ACTOR);
    }

    @Test
    void changeStatusMutatesLoadedTask() {
        Task task = task(99L);
        when(repository.findByIdAndWorkspaceId(1L, WS)).thenReturn(Optional.of(task));

        service.handle(new ChangeTaskStatusCommand(WS, ACTOR, 1L, TaskStatus.DONE));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(task.getUpdatedBy()).isEqualTo(ACTOR);
        verify(events).publishEvent(new AuditEvent("task.status_changed", ACTOR, WS, 1L));
    }

    @Test
    void deleteMissingTaskThrowsNotFound() {
        when(repository.findByIdAndWorkspaceId(99L, WS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(new DeleteTaskCommand(WS, ACTOR, 99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
        verify(repository, never()).delete(any());
    }

    @Test
    void updateWithStaleVersionIsRejected() {
        Task task = task(ACTOR);
        when(repository.findByIdAndWorkspaceId(1L, WS)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.handle(new UpdateTaskCommand(WS, ACTOR,
                1L, 5L, "Changed", null, TaskStatus.DONE, TaskPriority.LOW, null, null)))
                .isInstanceOf(ConflictException.class);
        assertThat(task.getTitle()).isEqualTo("Task");
    }

    @Test
    void assigningSomeoneOutsideTheWorkspaceIsRejected() {
        when(repository.findByIdAndWorkspaceId(1L, WS)).thenReturn(Optional.of(task(ACTOR)));
        when(access.isMember(WS, 42L)).thenReturn(false);

        assertThatThrownBy(() -> service.handle(new AssignTaskCommand(WS, ACTOR, 1L, 42L)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("42");
    }

    @Test
    void membersCanDeleteOnlyTheirOwnTasks() {
        Task mine = task(ACTOR);
        when(repository.findByIdAndWorkspaceId(1L, WS)).thenReturn(Optional.of(mine));
        service.handle(new DeleteTaskCommand(WS, ACTOR, 1L));
        verify(repository).delete(mine);

        when(repository.findByIdAndWorkspaceId(2L, WS)).thenReturn(Optional.of(task(99L)));
        assertThatThrownBy(() -> service.handle(new DeleteTaskCommand(WS, ACTOR, 2L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ownersCanDeleteAnyTask() {
        when(access.requireMember(WS, ACTOR)).thenReturn(WorkspaceRole.OWNER);
        Task theirs = task(99L);
        when(repository.findByIdAndWorkspaceId(1L, WS)).thenReturn(Optional.of(theirs));

        service.handle(new DeleteTaskCommand(WS, ACTOR, 1L));

        verify(repository).delete(theirs);
    }

    @Test
    void nonMembersAreStoppedBeforeTheTaskIsLoaded() {
        when(access.requireMember(WS, 7L)).thenThrow(new ResourceNotFoundException("Workspace", WS));

        assertThatThrownBy(() -> service.handle(new ChangeTaskStatusCommand(WS, 7L, 1L, TaskStatus.DONE)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).findByIdAndWorkspaceId(any(), any());
        org.mockito.Mockito.verifyNoInteractions(events);
    }

    @Test
    void blankTitleRejectedByDomain() {
        assertThatThrownBy(() -> service.handle(
                new CreateTaskCommand(WS, ACTOR, " ", null, TaskStatus.TODO, TaskPriority.LOW, null, null)))
                .isInstanceOf(DomainValidationException.class);
        verify(repository, never()).save(any());
    }
}
