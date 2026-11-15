package com.example.backend.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class AuditEventLoggerTest {
    @Test void logsCommittedChangesAndSkipsRollbacks() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuditEventLogger.class);
        var output = new ListAppender<ILoggingEvent>();
        output.start();
        logger.addAppender(output);
        try (var context = new AnnotationConfigApplicationContext(ListenerConfiguration.class)) {
            var transaction = new TransactionTemplate(new TestTransactionManager());
            transaction.executeWithoutResult(status -> {
                context.publishEvent(new AuditEvent("task.created", 1L, 10L, 42L));
                assertThat(output.list).isEmpty();
            });
            transaction.executeWithoutResult(status -> {
                context.publishEvent(new AuditEvent("task.deleted", 1L, 10L, 42L));
                status.setRollbackOnly();
            });
            assertThat(output.list).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                    .contains("operation=task.created", "actorId=1", "workspaceId=10", "resourceId=42"));
        } finally {
            logger.detachAppender(output);
            output.stop();
        }
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    @org.springframework.context.annotation.Import(AuditEventLogger.class)
    static class ListenerConfiguration { }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
