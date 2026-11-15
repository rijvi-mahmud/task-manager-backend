package com.example.backend.identity.command;

public record ResetPasswordCommand(String token, String newPassword) {

    @Override
    public String toString() {
        return "ResetPasswordCommand[token=***, newPassword=***]";
    }
}
