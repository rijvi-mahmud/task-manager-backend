package com.example.backend.workspace.query;

import com.example.backend.workspace.domain.WorkspaceRole;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(name = "Member", description = "A workspace member; tasks can be assigned to them")
public record WorkspaceMemberView(
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "User id", example = "1") Long id,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "Ada Lovelace") String name,
        @Schema(requiredMode = RequiredMode.REQUIRED, format = "email", example = "ada@example.com") String email,
        @Schema(requiredMode = RequiredMode.REQUIRED) WorkspaceRole role,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "Invited but not registered yet") Boolean pending) {
}
