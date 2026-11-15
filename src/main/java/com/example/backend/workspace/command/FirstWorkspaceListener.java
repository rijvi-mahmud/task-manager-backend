package com.example.backend.workspace.command;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.backend.identity.domain.UserRegisteredEvent;
import com.example.backend.workspace.domain.Workspace;
import com.example.backend.workspace.domain.WorkspaceMemberRepository;

/**
 * Gives a newly registered user somewhere to start. Someone who was invited
 * before registering already has a workspace, so they get none.
 */
@Component
public class FirstWorkspaceListener {

    private final WorkspaceCommandService commands;
    private final WorkspaceMemberRepository members;

    public FirstWorkspaceListener(WorkspaceCommandService commands, WorkspaceMemberRepository members) {
        this.commands = commands;
        this.members = members;
    }

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        if (!members.existsByUserId(event.userId())) {
            commands.handle(new CreateWorkspaceCommand(event.userId(), defaultName(event.displayName())));
        }
    }

    static String defaultName(String displayName) {
        String name = displayName + "'s workspace";
        return name.length() <= Workspace.NAME_MAX ? name : "My workspace";
    }
}
