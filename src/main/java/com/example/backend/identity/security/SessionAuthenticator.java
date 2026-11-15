package com.example.backend.identity.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Signs users in and out of the HTTP session for the JSON auth endpoints.
 *
 * <p>Does what Spring Security's form login would do, explicitly:
 * <ul>
 *   <li>a new session id on sign-in (prevents session fixation);</li>
 *   <li>a new CSRF token on sign-in and sign-out (the old one is discarded);</li>
 *   <li>the security context saved to the session, which Spring Session stores in PostgreSQL.</li>
 * </ul>
 */
@Component
public class SessionAuthenticator {
    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticator.class);
    private final AuthRateLimiter rateLimiter;

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final CsrfTokenRepository csrfTokenRepository;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public SessionAuthenticator(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                                CsrfTokenRepository csrfTokenRepository, AuthRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * Verifies the credentials and starts an authenticated session.
     *
     * @return the signed-in user
     * @throws org.springframework.security.core.AuthenticationException if the credentials are wrong
     */
    public AuthenticatedUser signIn(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        rateLimiter.checkAccount("login", email);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        } catch (AuthenticationException exception) {
            log.warn("Authentication failed reason={}", exception.getClass().getSimpleName());
            throw exception;
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        csrfTokenRepository.saveToken(null, request, response);
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        log.info("Session signed in userId={}", user.id());
        return user;
    }

    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = contextHolder.getContext().getAuthentication();
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user ? user.id() : null;
        logoutHandler.logout(request, response, authentication);
        log.info("Session signed out userId={}", userId);
        csrfTokenRepository.saveToken(null, request, response);
    }
}
