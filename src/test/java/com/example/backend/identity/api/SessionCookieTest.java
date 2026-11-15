package com.example.backend.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.example.backend.TestcontainersConfiguration;

/**
 * Cookie attributes as a real browser receives them, over real HTTP (MockMvc
 * re-renders Set-Cookie headers and drops attributes such as HttpOnly).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCookieTest {

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void sessionCookieIsHttpOnlyAndCsrfCookieIsReadableByTheSpa() throws Exception {
        HttpResponse<Void> csrf = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        String csrfCookie = setCookie(csrf.headers().allValues("Set-Cookie"), "XSRF-TOKEN");
        assertThat(csrfCookie).contains("SameSite=Lax").doesNotContain("HttpOnly");
        String token = csrfCookie.substring("XSRF-TOKEN=".length(), csrfCookie.indexOf(';'));

        String email = "cookie." + UUID.randomUUID().toString().substring(0, 8) + "@test.example";
        HttpResponse<String> registered = http.send(HttpRequest.newBuilder(uri("/api/v1/auth/register"))
                        .header("Content-Type", "application/json")
                        .header("Cookie", "XSRF-TOKEN=" + token)
                        .header("X-XSRF-TOKEN", token)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"" + email + "\",\"displayName\":\"Cookie\",\"password\":\"correct horse battery\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(setCookie(registered.headers().allValues("Set-Cookie"), "SESSION"))
                .contains("HttpOnly", "SameSite=Lax", "Path=/");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String setCookie(List<String> headers, String name) {
        return headers.stream().filter(h -> h.startsWith(name + "=") && !h.startsWith(name + "=;")).findFirst()
                .orElseThrow(() -> new AssertionError("No " + name + " cookie in " + headers));
    }
}
