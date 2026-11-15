package com.example.backend.identity.domain;

/**
 * Published (synchronously, in the registering transaction) when someone
 * creates or claims an account, so other modules can set it up, e.g. give
 * the user a first workspace.
 */
public record UserRegisteredEvent(Long userId, String displayName) {
}
