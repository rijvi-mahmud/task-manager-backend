package com.example.backend.identity.query;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

@Schema(name = "CurrentUser", description = "The signed-in user")
public record CurrentUserView(
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "1") Long id,
        @Schema(requiredMode = RequiredMode.REQUIRED, format = "email", example = "ada@example.com") String email,
        @Schema(requiredMode = RequiredMode.REQUIRED, example = "Ada Lovelace") String displayName) {
}
