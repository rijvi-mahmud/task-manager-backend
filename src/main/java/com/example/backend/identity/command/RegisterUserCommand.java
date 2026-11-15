package com.example.backend.identity.command;

public record RegisterUserCommand(String email, String displayName, String password) {

    @Override
    public String toString() {
        return "RegisterUserCommand[email=" + email + ", displayName=" + displayName + ", password=***]";
    }
}
