package com.example.backend.identity.command;

import java.net.URI;

/**
 * Delivers a password reset link. The default implementation writes it to
 * the server log (no mail server needed for a demo); production would
 * provide an email-sending implementation of this port.
 */
public interface PasswordResetLinkSender {

    void send(String email, URI link);
}
