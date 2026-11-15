package com.example.backend.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.backend.identity.security.AuthenticatedUser;

/** Signs MockMvc requests in as a given user without going through the login endpoint. */
public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static RequestPostProcessor as(Long userId) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "user" + userId + "@test.example", null);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }
}
