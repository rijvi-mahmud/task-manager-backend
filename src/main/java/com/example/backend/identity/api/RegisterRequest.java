package com.example.backend.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "A new account. If someone already added this email to a workspace, registering claims that invitation.")
public record RegisterRequest(
        @Schema(description = "Stored in lower case; must be unique", format = "email", example = "ada@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 120, message = "Email must be at most 120 characters")
        String email,

        @Schema(example = "Ada Lovelace")
        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String displayName,

        @Schema(description = "8 or more characters, at most 72 bytes", format = "password", example = "correct horse battery")
        @NotNull(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        String password) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + ", password=***]";
    }
}
