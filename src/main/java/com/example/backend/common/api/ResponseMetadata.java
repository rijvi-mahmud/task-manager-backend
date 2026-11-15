package com.example.backend.common.api;

import java.time.Instant;
import java.util.UUID;
import com.example.backend.common.observability.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;

public record ResponseMetadata(String requestId, Instant timestamp) {
    public static ResponseMetadata from(HttpServletRequest request) {
        Object value = request.getAttribute(RequestLoggingFilter.REQUEST_ID);
        String requestId = value instanceof String id ? id : UUID.randomUUID().toString();
        request.setAttribute(RequestLoggingFilter.REQUEST_ID, requestId);
        return new ResponseMetadata(requestId, Instant.now());
    }
}
