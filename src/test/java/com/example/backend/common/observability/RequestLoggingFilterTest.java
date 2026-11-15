package com.example.backend.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class RequestLoggingFilterTest {
    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final ListAppender<ILoggingEvent> output = new ListAppender<>();

    @BeforeEach void capture() { output.start(); logger.addAppender(output); }
    @AfterEach void release() { logger.detachAppender(output); output.stop(); MDC.clear(); }

    @Test void correlatesRequestsWithoutLoggingBodiesOrQueryStrings() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Request-ID", "request-123");
        request.setQueryString("token=secret-token");
        request.setContent("{\"password\":\"secret-password\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("request-123");
            response.setStatus(401);
        });
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("request-123");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(output.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("POST /api/v1/auth/login", "status=401", "durationMs=")
                    .doesNotContain("secret", "password", "token=");
            assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "request-123");
        });
    }

    @Test void replacesUnsafeRequestIdsAndRestoresMdcOnFailure() {
        var request = new MockHttpServletRequest("GET", "/api/v1/workspaces");
        request.addHeader("X-Request-ID", "forged\nentry");
        var response = new MockHttpServletResponse();
        MDC.put("requestId", "outer-request");
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("failure");
        })).isInstanceOf(ServletException.class);
        assertThat(response.getHeader("X-Request-ID")).matches("[a-f0-9-]{36}");
        assertThat(MDC.get("requestId")).isEqualTo("outer-request");
        assertThat(output.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("status=500");
        });
    }
}
