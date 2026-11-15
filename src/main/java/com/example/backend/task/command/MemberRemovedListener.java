package com.example.backend.task.command;

import java.time.Clock;
import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.backend.task.domain.TaskRepository;
import com.example.backend.workspace.domain.MemberRemovedEvent;

/**
 * Unassigns a member's tasks in a workspace before the membership is deleted
 * (same transaction), bumping each task's version so open editors see the change.
 */
@Component
public class MemberRemovedListener {

    private final TaskRepository repository;
    private final Clock clock;

    public MemberRemovedListener(TaskRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @EventListener
    public void onMemberRemoved(MemberRemovedEvent event) {
        repository.unassignAllIn(event.workspaceId(), event.userId(), event.actorId(), Instant.now(clock));
    }
}
