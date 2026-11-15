package com.example.backend.workspace.api;

import static com.example.backend.support.AuthTestSupport.as;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.TestcontainersConfiguration;
import com.example.backend.identity.command.UserCommandService;
import com.example.backend.support.Accounts;
import com.example.backend.support.Accounts.TestUser;
import com.example.backend.workspace.command.WorkspaceCommandService;

/** Who can see and change what: the collaboration rules over HTTP. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class WorkspaceApiTest {

    @Autowired MockMvc mvc;
    @Autowired UserCommandService users;
    @Autowired WorkspaceCommandService workspaces;

    TestUser owner;
    TestUser member;
    TestUser outsider;
    Long ws;
    String base;

    @BeforeEach
    void setUp() {
        Accounts accounts = new Accounts(users, workspaces);
        owner = accounts.user("Owner");
        member = accounts.user("Member");
        outsider = accounts.user("Outsider");
        ws = accounts.workspace(owner, member);
        base = "/api/v1/workspaces/" + ws;
    }

    @Test
    void membersSeeTheWorkspaceWithTheirRole() throws Exception {
        mvc.perform(get(base).with(as(member.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("MEMBER"))
                .andExpect(jsonPath("$.data.memberCount").value(2));
        mvc.perform(get(base + "/members").with(as(member.id())))
                .andExpect(jsonPath("$.data[*].name", containsInAnyOrder("Owner", "Member")));
    }

    @Test
    void outsidersCannotTellTheWorkspaceExists() throws Exception {
        mvc.perform(get(base).with(as(outsider.id()))).andExpect(status().isNotFound());
        mvc.perform(get(base + "/tasks").with(as(outsider.id()))).andExpect(status().isNotFound());
        mvc.perform(get(base + "/members").with(as(outsider.id()))).andExpect(status().isNotFound());
        mvc.perform(post(base + "/tasks").with(as(outsider.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"status\":\"TODO\",\"priority\":\"LOW\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/workspaces").with(as(outsider.id())))
                .andExpect(jsonPath("$.data[*].id", contains(org.hamcrest.Matchers.not(ws.intValue()))));
    }

    @Test
    void onlyOwnersManageTheWorkspace() throws Exception {
        mvc.perform(put(base).with(as(member.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mine now\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only workspace owners can rename the workspace."));
        mvc.perform(post(base + "/members").with(as(member.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + outsider.email() + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(base + "/members/" + owner.id()).with(as(member.id())).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete(base).with(as(member.id())).with(csrf())).andExpect(status().isForbidden());

        mvc.perform(put(base).with(as(owner.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Renamed \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed"));
    }

    @Test
    void addingSomeoneTwiceIsAConflictAndEmailsAreCaseInsensitive() throws Exception {
        mvc.perform(post(base + "/members").with(as(owner.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + member.email().toUpperCase() + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void membersCanLeaveButTheLastOwnerCannot() throws Exception {
        mvc.perform(delete(base + "/members/" + member.id()).with(as(member.id())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(base).with(as(member.id()))).andExpect(status().isNotFound());

        mvc.perform(delete(base + "/members/" + owner.id()).with(as(owner.id())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A workspace needs at least one owner. Delete the workspace instead."));
    }

    @Test
    void deletingTheWorkspaceDeletesItsTasks() throws Exception {
        mvc.perform(post(base + "/tasks").with(as(member.id())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Doomed\",\"status\":\"TODO\",\"priority\":\"LOW\"}"))
                .andExpect(status().isCreated());

        mvc.perform(delete(base).with(as(owner.id())).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(base + "/tasks").with(as(owner.id()))).andExpect(status().isNotFound());
    }
}
