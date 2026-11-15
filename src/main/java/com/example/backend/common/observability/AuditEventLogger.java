package com.example.backend.common.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Successful changes are logged only after their transaction commits. */
@Component
public class AuditEventLogger {
    private static final Logger log = LoggerFactory.getLogger(AuditEventLogger.class);

    @TransactionalEventListener
    public void onCommitted(AuditEvent event) {
        log.info("operation={} actorId={} workspaceId={} resourceId={}",
                event.operation(), event.actorId(), event.workspaceId(), event.resourceId());
    }
}
