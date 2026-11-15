package com.example.backend.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Email and password")
public record LoginRequest(
        @Schema(format = "email", example = "ada@example.com")
        @NotBlank(message = "Email is required")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String email,

        @Schema(format = "password", example = "correct horse battery")
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
