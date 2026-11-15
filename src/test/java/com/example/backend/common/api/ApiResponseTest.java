package com.example.backend.common.api;

import static com.example.backend.support.AuthTestSupport.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import com.example.backend.config.SecurityConfig;
import com.example.backend.config.WebConfig;
import com.example.backend.identity.security.AuthRateLimiter;

@WebMvcTest(controllers = ApiResponseTest.Endpoints.class, properties = {
        "app.cors.allowed-origins=http://localhost:5173", "app.auth.rate-limit.attempts-per-ip=1"})
@Import({SecurityConfig.class, WebConfig.class, AuthRateLimiter.class, ApiResponseTest.TestClock.class,
        ApiResponseTest.Endpoints.class})
class ApiResponseTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserDetailsService userDetailsService;

    @Test void wrapsSuccessAndCorrelatesMetadata() throws Exception {
        mvc.perform(get("/api/v1/logging-test").with(as(1L)).header("X-Request-ID", "success-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.meta.requestId").value("success-test"))
                .andExpect(jsonPath("$.meta.timestamp").isString())
                .andExpect(header().string("X-Request-ID", "success-test"));
    }

    @Test void preservesProblemsIncludingSecurityFilterErrors() throws Exception {
        mvc.perform(get("/api/v1/logging-test").header("X-Request-ID", "unauthorized-test"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.requestId").value("unauthorized-test"))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test void unexpectedFailuresHideInternalsAndCarryRequestId() throws Exception {
        mvc.perform(get("/api/v1/logging-test/failure").with(as(1L)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.requestId").isString());
    }

    @Test void keepsNoContentResponsesEmpty() throws Exception {
        mvc.perform(get("/api/v1/logging-test/empty").with(as(1L)))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test void rateLimitReturnsCorrelatedProblemAndRetryAfter() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(csrf())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).header("X-Request-ID", "limited-test"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.requestId").value("limited-test"));
    }

    @RestController
    @RequestMapping(path = "/api/{version}", version = ApiVersions.V1)
    static class Endpoints {
        @GetMapping("/logging-test") Map<String, Long> success() { return Map.of("id", 42L); }
        @GetMapping("/logging-test/failure") void failure() { throw new IllegalStateException("internal-secret"); }
        @GetMapping("/logging-test/empty") @ResponseStatus(HttpStatus.NO_CONTENT) void empty() { }
        @PostMapping("/auth/login") Map<String, Long> login() { return Map.of("id", 1L); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {
        @Bean Clock clock() { return Clock.fixed(java.time.Instant.parse("2026-11-15T10:00:00Z"), java.time.ZoneOffset.UTC); }
    }
}
