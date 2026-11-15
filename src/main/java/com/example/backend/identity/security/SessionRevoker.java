package com.example.backend.identity.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.backend.identity.domain.PasswordChangedEvent;

/**
 * After a password reset, signs the user out on every device: whoever knew
 * the old password must not keep a session.
 */
@Component
public class SessionRevoker {
    private static final Logger log = LoggerFactory.getLogger(SessionRevoker.class);

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    @TransactionalEventListener
    public void onPasswordChanged(PasswordChangedEvent event) {
        var existing = sessions.findByPrincipalName(event.email());
        existing.keySet().forEach(sessions::deleteById);
        log.info("Sessions revoked userId={} sessionCount={}", event.userId(), existing.size());
    }
}
