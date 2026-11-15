package com.example.backend.task.api;

import static com.example.backend.support.AuthTestSupport.as;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.TestcontainersConfiguration;
import com.example.backend.identity.command.UserCommandService;
import com.example.backend.support.Accounts;
import com.example.backend.support.Accounts.TestUser;
import com.example.backend.workspace.command.WorkspaceCommandService;
import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end task API tests: real HTTP layer, security, validation, services and
 * the PostgreSQL schema (Testcontainers) created by the Flyway migrations. Each test rolls back.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class TaskApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserCommandService users;
    @Autowired WorkspaceCommandService workspaces;

    TestUser owner;
    TestUser member;
    String tasks;

    @BeforeEach
    void setUp() {
        Accounts accounts = new Accounts(users, workspaces);
        owner = accounts.user("Owner");
        member = accounts.user("Dev");
        tasks = "/api/v1/workspaces/" + accounts.workspace(owner, member) + "/tasks";
    }

    @Test
    void createReturns201WithLocationAndView() throws Exception {
        postJson(owner, tasks, """
                {"title":"  Ship it ","status":"TODO","priority":"HIGH","dueDate":"2000-01-01"}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "http://localhost/api/v1/workspaces/\\d+/tasks/\\d+")))
                .andExpect(jsonPath("$.data.title").value("Ship it"))
                .andExpect(jsonPath("$.data.overdue").value(true))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.createdById").value(owner.id()))
                .andExpect(jsonPath("$.data.assigneeId").value(nullValue()));
    }

    @Test
    void invalidPayloadReturns400WithFieldErrors() throws Exception {
        postJson(owner, tasks, """
                {"title":" ","status":"TODO"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Title is required"))
                .andExpect(jsonPath("$.errors.priority").value("Priority is required"));
    }

    @Test
    void assigningANonMemberReturns400() throws Exception {
        postJson(owner, tasks, """
                {"title":"Task","status":"TODO","priority":"LOW","assigneeId":999999}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("User 999999 is not a member of this workspace"));
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        long id = createTask(owner, "Original");

        putJson(owner, tasks + "/" + id, """
                {"title":"First edit","status":"TODO","priority":"LOW","version":0}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        putJson(member, tasks + "/" + id, """
                {"title":"Stale edit","status":"TODO","priority":"LOW","version":0}
                """)
                .andExpect(status().isConflict());

        mvc.perform(get(tasks + "/" + id).with(as(owner.id()))).andExpect(jsonPath("$.data.title").value("First edit"));
    }

    @Test
    void assignAndFilterByAssignee() throws Exception {
        long id = createTask(owner, "Assigned");

        patchJson(owner, tasks + "/" + id + "/assignee", "{\"assigneeId\":" + member.id() + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assigneeId").value(member.id()))
                .andExpect(jsonPath("$.data.assigneeName").value("Dev"));

        mvc.perform(get(tasks).with(as(member.id())).param("assigneeId", String.valueOf(member.id())))
                .andExpect(jsonPath("$.data.items[*].title").value(hasItem("Assigned")));
    }

    @Test
    void listReturnsAPageEnvelope() throws Exception {
        createTask(owner, "Paged");
        mvc.perform(get(tasks).with(as(member.id())).param("q", "paged").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("Paged"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1));
    }

    @Test
    void invalidPagingParametersReturn400WithFieldErrors() throws Exception {
        mvc.perform(get(tasks).with(as(owner.id())).param("size", "500").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").value("Size must be at most 100"))
                .andExpect(jsonPath("$.errors.page").value("Page must be 0 or more"));
    }

    @Test
    void assigneeAndUnassignedTogetherIs400() throws Exception {
        mvc.perform(get(tasks).with(as(owner.id())).param("assigneeId", "1").param("unassigned", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSortIs400() throws Exception {
        mvc.perform(get(tasks).with(as(owner.id())).param("sort", "RANDOM")).andExpect(status().isBadRequest());
    }

    @Test
    void statsEndpointReturnsCounts() throws Exception {
        createTask(owner, "Counted");
        mvc.perform(get(tasks + "/stats").with(as(owner.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.overdue").value(0));
    }

    @Test
    void changeStatusAndDeleteLifecycle() throws Exception {
        long id = createTask(member, "Lifecycle");

        patchJson(owner, tasks + "/" + id + "/status", "{\"status\":\"DONE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));

        mvc.perform(withCsrf(delete(tasks + "/" + id), member)).andExpect(status().isNoContent());
        mvc.perform(get(tasks + "/" + id).with(as(owner.id()))).andExpect(status().isNotFound());
    }

    @Test
    void membersCannotDeleteOthersTasksButOwnersCan() throws Exception {
        long id = createTask(owner, "Owner's task");

        mvc.perform(withCsrf(delete(tasks + "/" + id), member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only the task's creator or a workspace owner can delete it."));

        long memberTask = createTask(member, "Member's task");
        mvc.perform(withCsrf(delete(tasks + "/" + memberTask), owner)).andExpect(status().isNoContent());
    }

    @Test
    void writesWithoutACsrfTokenAreRejected() throws Exception {
        mvc.perform(post(tasks).with(as(owner.id())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":\"TODO\",\"priority\":\"LOW\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Missing or invalid CSRF token")));
    }

    private long createTask(TestUser user, String title) throws Exception {
        String body = postJson(user, tasks, "{\"title\":\"" + title + "\",\"status\":\"TODO\",\"priority\":\"MEDIUM\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, TestUser user) {
        return request.with(as(user.id())).with(csrf());
    }

    private ResultActions postJson(TestUser user, String url, String json) throws Exception {
        return mvc.perform(withCsrf(post(url), user).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putJson(TestUser user, String url, String json) throws Exception {
        return mvc.perform(withCsrf(put(url), user).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchJson(TestUser user, String url, String json) throws Exception {
        return mvc.perform(withCsrf(patch(url), user).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
