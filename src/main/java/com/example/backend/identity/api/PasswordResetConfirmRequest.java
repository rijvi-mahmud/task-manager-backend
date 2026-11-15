package com.example.backend.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "The token from the reset link and the new password")
public record PasswordResetConfirmRequest(
        @Schema(description = "The `token` query parameter of the reset link")
        @NotBlank(message = "Token is required")
        @Size(max = 100, message = "Token is invalid")
        String token,

        @Schema(description = "8 or more characters, at most 72 bytes", format = "password")
        @NotNull(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String newPassword) {

    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[token=***, newPassword=***]";
    }
}
