package com.example.backend.support;

import java.util.UUID;

import com.example.backend.identity.command.RegisterUserCommand;
import com.example.backend.identity.command.UserCommandService;
import com.example.backend.workspace.command.AddMemberCommand;
import com.example.backend.workspace.command.CreateWorkspaceCommand;
import com.example.backend.workspace.command.WorkspaceCommandService;

/** Creates users and workspaces through the real command services for integration tests. */
public final class Accounts {

    public static final String PASSWORD = "correct horse battery";

    private final UserCommandService users;
    private final WorkspaceCommandService workspaces;

    public Accounts(UserCommandService users, WorkspaceCommandService workspaces) {
        this.users = users;
        this.workspaces = workspaces;
    }

    public record TestUser(Long id, String email, String name) {
    }

    /** A registered user with a unique email (registration also gives them a first workspace). */
    public TestUser user(String name) {
        String email = name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.example";
        Long id = users.handle(new RegisterUserCommand(email, name, PASSWORD));
        return new TestUser(id, email, name);
    }

    /** @return id of a new workspace owned by {@code owner}, with {@code members} added as MEMBER */
    public Long workspace(TestUser owner, TestUser... members) {
        Long id = workspaces.handle(new CreateWorkspaceCommand(owner.id(), "Board of " + owner.name()));
        for (TestUser member : members) {
            workspaces.handle(new AddMemberCommand(id, owner.id(), member.email()));
        }
        return id;
    }
}
