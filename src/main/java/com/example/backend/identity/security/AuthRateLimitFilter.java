package com.example.backend.identity.security;

import java.io.IOException;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import com.example.backend.common.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Runs before password hashing and uses the servlet's trusted remote address. */
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private static final Map<String, String> OPERATIONS = Map.of(
            "/api/v1/auth/login", "login",
            "/api/v1/auth/register", "register",
            "/api/v1/auth/password-reset", "password-reset",
            "/api/v1/auth/password-reset/confirm", "password-reset-confirm");
    private final AuthRateLimiter limiter;
    private final HandlerExceptionResolver errors;

    public AuthRateLimitFilter(AuthRateLimiter limiter, HandlerExceptionResolver errors) {
        this.limiter = limiter;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String operation = "POST".equals(request.getMethod()) ? OPERATIONS.get(path) : null;
        try {
            if (operation != null) limiter.checkIp(operation, request.getRemoteAddr());
        } catch (RateLimitExceededException exception) {
            errors.resolveException(request, response, null, exception);
            return;
        }
        chain.doFilter(request, response);
    }
}
