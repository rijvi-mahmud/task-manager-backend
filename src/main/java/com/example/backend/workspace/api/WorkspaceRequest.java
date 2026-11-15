package com.example.backend.workspace.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Workspace details")
public record WorkspaceRequest(
        @Schema(description = "Trimmed before saving", example = "Launch team")
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String name) {
}
