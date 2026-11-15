package com.example.backend.common.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Correlates request logs without recording credentials, bodies or query strings. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "requestId";
    public static final String HEADER = "X-Request-ID";
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String requestId = supplied != null && supplied.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
                ? supplied : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        String previous = MDC.get(REQUEST_ID);
        MDC.put(REQUEST_ID, requestId);
        long started = System.nanoTime();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            int status = completed ? response.getStatus() : 500;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            String path = request.getRequestURI().replaceAll("[\\r\\n\\t]", "_");
            if (status >= 500) log.error("{} {} status={} durationMs={}", request.getMethod(), path, status, durationMs);
            else if (status >= 400) log.warn("{} {} status={} durationMs={}", request.getMethod(), path, status, durationMs);
            else log.info("{} {} status={} durationMs={}", request.getMethod(), path, status, durationMs);
            if (previous == null) MDC.remove(REQUEST_ID);
            else MDC.put(REQUEST_ID, previous);
        }
    }
}
