package com.example.backend.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Account to send a reset link to")
public record PasswordResetRequest(
        @Schema(format = "email", example = "ada@example.com")
        @NotBlank(message = "Email is required")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String email) {
}
