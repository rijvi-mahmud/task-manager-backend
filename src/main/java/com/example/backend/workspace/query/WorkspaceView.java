package com.example.backend.workspace.query;

import com.example.backend.workspace.domain.WorkspaceRole;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(name = "Workspace", description = "A workspace the current user belongs to")
public record WorkspaceView(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "1") Long id,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "Launch team") String name,
        @Schema(requiredMode = RequiredMode.REQUIRED, description = "The current user's role here") WorkspaceRole role,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "4") Long memberCount) {
}
