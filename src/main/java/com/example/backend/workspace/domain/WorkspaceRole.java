package com.example.backend.workspace.domain;

public enum WorkspaceRole {
    /** Manages the workspace and its members, and may delete any task. */
    OWNER,
    /** Creates and works on tasks; may delete the tasks they created. */
    MEMBER
}
