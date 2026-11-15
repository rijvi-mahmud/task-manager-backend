package com.example.backend.task.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.example.backend.config.SecurityConfig;
import com.example.backend.config.WebConfig;
import org.springframework.security.core.userdetails.UserDetailsService;
import static com.example.backend.support.AuthTestSupport.as;
import com.example.backend.task.command.TaskCommandService;
import com.example.backend.task.query.TaskQueryService;
import com.example.backend.task.query.TaskSearch;
import com.example.backend.task.query.TaskSort;

@WebMvcTest(controllers = TaskController.class, properties = "app.cors.allowed-origins=http://localhost:5173")
@Import({WebConfig.class, SecurityConfig.class})
class TaskDateRangeApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean TaskCommandService commands;
    @MockitoBean TaskQueryService queries;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean com.example.backend.identity.security.AuthRateLimiter authRateLimiter;

    static final String TASKS = "/api/v1/workspaces/10/tasks";

    @Test
    void bindsIsoCalendarDatesAndForwardsBothBounds() throws Exception {
        when(queries.find(eq(10L), eq(1L), any(TaskSearch.class), eq(TaskSort.NEWEST), eq(0), eq(25))).thenReturn(Page.empty());
        mvc.perform(get(TASKS).with(as(1L)).param("dueFrom", "2024-02-29").param("dueTo", "2024-03-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0));
        ArgumentCaptor<TaskSearch> search = ArgumentCaptor.forClass(TaskSearch.class);
        verify(queries).find(eq(10L), eq(1L), search.capture(), eq(TaskSort.NEWEST), eq(0), eq(25));
        assertThat(search.getValue().dueFrom()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(search.getValue().dueTo()).isEqualTo(LocalDate.of(2024, 3, 1));
    }

    @Test
    void rejectsMalformedImpossibleAndUnsupportedDates() throws Exception {
        for (String date : new String[] {"not-a-date", "2026-02-30", "0000-01-01", "+10000-01-01"}) {
            mvc.perform(get(TASKS).with(as(1L)).param("dueFrom", date)).andExpect(status().isBadRequest());
            mvc.perform(get(TASKS).with(as(1L)).param("dueTo", date)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(queries);
    }

    @Test
    void rejectsReversedRangesBeforeQuerying() throws Exception {
        mvc.perform(get(TASKS).with(as(1L)).param("dueFrom", "2026-03-02").param("dueTo", "2026-03-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Due from must be on or before due to"));
        verifyNoInteractions(queries);
    }

    @Test
    void requiresASession() throws Exception {
        mvc.perform(get(TASKS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Sign in to continue."));
        verifyNoInteractions(queries);
    }
}
