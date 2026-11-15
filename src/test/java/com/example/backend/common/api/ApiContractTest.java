package com.example.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.backend.support.AuthTestSupport.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.backend.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;

/**
 * Cross-cutting HTTP contract: URI versioning, RFC 9457 errors for framework
 * failures, and the shape of the published OpenAPI document.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
class ApiContractTest {

    @Autowired
    MockMvc mvc;

    @Test
    void servesTheCurrentVersion() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v2/auth/csrf", "/api/1/auth/csrf", "/api/V1/auth/csrf"})
    void rejectsUnsupportedOrNonCanonicalVersionsAsProblem(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Invalid API version")));
    }

    @Test
    void healthEndpointIsNotCaughtByApiVersioning() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void malformedJsonIsAProblemResponse() throws Exception {
        mvc.perform(post("/api/v1/workspaces").with(as(1L)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void validationFailureIsAProblemResponseWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/workspaces").with(as(1L)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.name").value("Name is required"));
    }

    @Test
    void unsupportedMediaTypeIsAProblemResponse() throws Exception {
        mvc.perform(post("/api/v1/workspaces").with(as(1L)).with(csrf()).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void authenticationFailuresAreProblemResponses() throws Exception {
        mvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(post("/api/v1/workspaces").with(as(1L)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void openApiDocumentDescribesTheVersionedApi() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(json, "$.openapi")).startsWith("3.1");
        assertThat(JsonPath.<String>read(json, "$.info.version")).isEqualTo(ApiVersions.V1);
        assertThat(JsonPath.<List<Map<String, Object>>>read(json, "$.security"))
                .as("session required by default").containsExactly(Map.of("session", List.of()));
        assertThat(JsonPath.<String>read(json, "$.components.securitySchemes.session.in")).isEqualTo("cookie");
        assertThat(JsonPath.<List<?>>read(json, "$.paths['/api/v1/auth/login'].post.security")).as("public").isEmpty();
        assertThat(JsonPath.<String>read(json, "$.servers[0].url")).as("deployment-independent server").isEqualTo("/");

        Map<String, Map<String, Map<String, Object>>> paths = JsonPath.read(json, "$.paths");
        assertThat(paths.keySet()).isNotEmpty().allMatch(path -> path.startsWith("/api/v1/"));

        Set<String> operationIds = new HashSet<>();
        List<String> problems = new ArrayList<>();
        paths.forEach((path, operations) -> operations.forEach((method, op) -> {
            String where = method.toUpperCase() + " " + path;
            Object id = op.get("operationId");
            if (id == null || !operationIds.add(id.toString())) {
                problems.add(where + ": missing or duplicate operationId");
            }
            if (op.get("summary") == null || op.get("tags") == null) {
                problems.add(where + ": missing summary or tag");
            }
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> responses = (Map<String, Map<String, Object>>) op.get("responses");
            if (responses.keySet().stream().noneMatch(code -> code.startsWith("4"))) {
                problems.add(where + ": no 4xx response documented");
            }
            boolean needsSession = op.get("security") == null || !((List<?>) op.get("security")).isEmpty();
            if (needsSession && !responses.containsKey("401")) {
                problems.add(where + ": session-protected but no 401 documented");
            }
            if (!"get".equals(method) && !responses.containsKey("403")) {
                problems.add(where + ": unsafe method but no 403 (CSRF) documented");
            }
            responses.forEach((code, response) -> {
                if (code.startsWith("4") && !String.valueOf(response.get("$ref")).startsWith("#/components/responses/")) {
                    problems.add(where + " " + code + ": error not using a shared problem response");
                }
            });
        }));
        assertThat(problems).isEmpty();

        assertThat(JsonPath.<Map<String, Object>>read(json, "$.components.responses.NotFound.content"))
                .containsKey(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(JsonPath.<String>read(json, "$.paths['/api/v1/workspaces/{workspaceId}/tasks'].post.responses['201'].headers.Location.schema.format"))
                .isEqualTo("uri");
    }
}
