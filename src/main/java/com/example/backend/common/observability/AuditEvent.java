package com.example.backend.common.observability;

/** Identifier-only audit information; never includes request bodies or credentials. */
public record AuditEvent(String operation, Long actorId, Long workspaceId, Long resourceId) { }
