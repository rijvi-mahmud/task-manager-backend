package com.example.backend.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.backend.TestcontainersConfiguration;
import com.example.backend.identity.command.PasswordResetLinkSender;
import com.example.backend.support.Browser;
import com.jayway.jsonpath.JsonPath;

/**
 * The real browser flow: session cookie, CSRF double-submit, sign-in, sign-out
 * and password reset, through every filter. Not transactional, because session
 * revocation runs after the reset commits; every test uses fresh emails.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class AuthApiTest {

    static final String AUTH = "/api/v1/auth";
    static final String PASSWORD = "correct horse battery";

    @Autowired MockMvc mvc;
    @MockitoBean PasswordResetLinkSender linkSender;

    @Test
    void registeringSignsInAndCreatesAFirstWorkspace() throws Exception {
        Browser browser = new Browser(mvc);
        String email = uniqueEmail("ada");

        MvcResult registered = browser.post(AUTH + "/register", registerJson(email.toUpperCase(), "Ada"));
        assertThat(registered.getResponse().getStatus()).isEqualTo(201);
        assertThat(registered.getResponse().getHeader("Location")).isEqualTo("http://localhost/api/v1/auth/me");
        assertThat(browser.cookie("SESSION")).isNotBlank();

        MvcResult me = browser.get(AUTH + "/me");
        assertThat(me.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<String>read(me.getResponse().getContentAsString(), "$.data.email")).isEqualTo(email);

        String workspaces = browser.get("/api/v1/workspaces").getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(workspaces, "$.data[*].name")).containsExactly("Ada's workspace");
        assertThat(JsonPath.<List<String>>read(workspaces, "$.data[*].role")).containsExactly("OWNER");
    }

    @Test
    void duplicateRegistrationIsAConflict() throws Exception {
        String email = uniqueEmail("dup");
        new Browser(mvc).post(AUTH + "/register", registerJson(email, "First"));
        assertThat(new Browser(mvc).post(AUTH + "/register", registerJson(email, "Second")).getResponse().getStatus())
                .isEqualTo(409);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        String email = uniqueEmail("login");
        new Browser(mvc).post(AUTH + "/register", registerJson(email, "Login"));

        MvcResult wrongPassword = new Browser(mvc).post(AUTH + "/login", loginJson(email, "not the password"));
        MvcResult unknownEmail = new Browser(mvc).post(AUTH + "/login", loginJson(uniqueEmail("nobody"), PASSWORD));

        for (MvcResult result : List.of(wrongPassword, unknownEmail)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.detail"))
                    .isEqualTo("Invalid email or password.");
        }
    }

    @Test
    void protectedEndpointsNeedASessionAndWritesNeedTheCsrfHeader() throws Exception {
        Browser browser = new Browser(mvc);
        assertThat(browser.get(AUTH + "/me").getResponse().getStatus()).isEqualTo(401);

        String email = uniqueEmail("csrf");
        browser.post(AUTH + "/register", registerJson(email, "Csrf"));
        // Signed in, but a forged request cannot know the token.
        assertThat(browser.postWithoutCsrfHeader("/api/v1/workspaces", "{\"name\":\"Forged\"}").getResponse().getStatus())
                .isEqualTo(403);
        assertThat(browser.post("/api/v1/workspaces", "{\"name\":\"Real\"}").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void signingInRotatesTheCsrfTokenAndSigningOutEndsTheSession() throws Exception {
        String email = uniqueEmail("logout");
        new Browser(mvc).post(AUTH + "/register", registerJson(email, "Logout"));

        Browser browser = new Browser(mvc);
        browser.get(AUTH + "/csrf");
        String before = browser.cookie("XSRF-TOKEN");
        assertThat(browser.post(AUTH + "/login", loginJson(email, PASSWORD)).getResponse().getStatus()).isEqualTo(200);
        assertThat(browser.cookie("XSRF-TOKEN")).as("token discarded on sign-in").isNull();
        assertThat(browser.get(AUTH + "/me").getResponse().getStatus()).isEqualTo(200);
        assertThat(browser.cookie("XSRF-TOKEN")).isNotEqualTo(before);

        assertThat(browser.post(AUTH + "/logout", "{}").getResponse().getStatus()).isEqualTo(204);
        assertThat(browser.get(AUTH + "/me").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void passwordResetWorksOnceAndSignsOutEverySession() throws Exception {
        String email = uniqueEmail("reset");
        Browser oldDevice = new Browser(mvc);
        oldDevice.post(AUTH + "/register", registerJson(email, "Reset"));

        Browser browser = new Browser(mvc);
        assertThat(browser.post(AUTH + "/password-reset", "{\"email\":\"" + uniqueEmail("ghost") + "\"}")
                .getResponse().getStatus()).isEqualTo(202);
        verify(linkSender, never()).send(any(), any());

        assertThat(browser.post(AUTH + "/password-reset", "{\"email\":\"" + email + "\"}").getResponse().getStatus())
                .isEqualTo(202);
        ArgumentCaptor<URI> link = ArgumentCaptor.forClass(URI.class);
        verify(linkSender).send(eq(email), link.capture());
        assertThat(link.getValue().toString()).startsWith("http://localhost:5173/reset-password?token=");
        String token = UriComponentsBuilder.fromUri(link.getValue()).build().getQueryParams().getFirst("token");

        String confirm = "{\"token\":\"" + token + "\",\"newPassword\":\"a brand new password\"}";
        assertThat(browser.post(AUTH + "/password-reset/confirm", confirm).getResponse().getStatus()).isEqualTo(204);
        assertThat(browser.post(AUTH + "/password-reset/confirm", confirm).getResponse().getStatus())
                .as("links are single use").isEqualTo(400);

        assertThat(oldDevice.get(AUTH + "/me").getResponse().getStatus()).as("old session revoked").isEqualTo(401);
        assertThat(browser.post(AUTH + "/login", loginJson(email, PASSWORD)).getResponse().getStatus()).isEqualTo(401);
        assertThat(browser.post(AUTH + "/login", loginJson(email, "a brand new password")).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void registeringWithAnInvitedEmailJoinsTheWorkspace() throws Exception {
        Browser owner = new Browser(mvc);
        owner.post(AUTH + "/register", registerJson(uniqueEmail("owner"), "Owner"));
        Long workspaceId = ((Number) JsonPath.read(owner.get("/api/v1/workspaces").getResponse().getContentAsString(), "$.data[0].id")).longValue();

        String invitee = uniqueEmail("grace");
        MvcResult added = owner.post("/api/v1/workspaces/" + workspaceId + "/members", "{\"email\":\"" + invitee + "\"}");
        assertThat(added.getResponse().getStatus()).isEqualTo(201);
        assertThat(JsonPath.<Boolean>read(added.getResponse().getContentAsString(), "$.data.pending")).isTrue();

        // Invited but not registered: cannot sign in yet.
        assertThat(new Browser(mvc).post(AUTH + "/login", loginJson(invitee, PASSWORD)).getResponse().getStatus()).isEqualTo(401);

        Browser grace = new Browser(mvc);
        assertThat(grace.post(AUTH + "/register", registerJson(invitee, "Grace")).getResponse().getStatus()).isEqualTo(201);
        String workspaces = grace.get("/api/v1/workspaces").getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(workspaces, "$.data[*].id")).containsExactly(workspaceId.intValue());
        assertThat(JsonPath.<List<String>>read(workspaces, "$.data[*].role")).containsExactly("MEMBER");
    }

    @Test
    void passwordsAreValidated() throws Exception {
        MvcResult result = new Browser(mvc).post(AUTH + "/register", registerJson(uniqueEmail("short"), "Short", "short"));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.errors.password"))
                .isEqualTo("Password must be 8 to 72 characters");
    }

    private static String uniqueEmail(String name) {
        return name + "." + UUID.randomUUID().toString().substring(0, 8) + "@test.example";
    }

    private static String registerJson(String email, String name) {
        return registerJson(email, name, PASSWORD);
    }

    private static String registerJson(String email, String name, String password) {
        return "{\"email\":\"" + email + "\",\"displayName\":\"" + name + "\",\"password\":\"" + password + "\"}";
    }

    private static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
