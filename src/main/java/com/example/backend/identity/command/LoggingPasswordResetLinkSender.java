package com.example.backend.identity.command;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Intentional development-only delivery: writes the reset link to local logs.
 * These links are credentials; access to this logger's output grants reset access.
 * No email server or external delivery integration is used by this project.
 */
@Component
public class LoggingPasswordResetLinkSender implements PasswordResetLinkSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetLinkSender.class);

    @Override
    public void send(String email, URI link) {
        log.info("Password reset requested for {}. Reset link (valid 30 minutes): {}", email, link);
    }
}
