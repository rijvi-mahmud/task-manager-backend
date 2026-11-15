package com.example.backend.task.query;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import com.example.backend.workspace.WorkspaceAccess;

/** "Today" (and so "overdue") follows the Clock's business zone, not UTC or the server's zone. */
class TaskQueryServiceTest {

    // 20:00 UTC on Jan 14 is already 02:00 on Jan 15 in Dhaka (UTC+6).
    private static final Instant NOW = Instant.parse("2026-01-14T20:00:00Z");

    private static final Long WS = 10L;
    private static final Long USER = 1L;

    private final TaskReadRepository repository = mock(TaskReadRepository.class);
    private final WorkspaceAccess access = mock(WorkspaceAccess.class);

    @Test
    void todayIsTakenInTheBusinessTimeZone() {
        new TaskQueryService(repository, access, Clock.fixed(NOW, ZoneId.of("Asia/Dhaka"))).stats(WS, USER);
        verify(repository).stats(WS, LocalDate.of(2026, 1, 15));
    }

    @Test
    void theSameInstantIsStillThePreviousDayInUtc() {
        new TaskQueryService(repository, access, Clock.fixed(NOW, ZoneId.of("UTC"))).stats(WS, USER);
        verify(repository).stats(WS, LocalDate.of(2026, 1, 14));
    }

    @Test
    void forwardsBothDueDateBoundsForEverySort() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        TaskSearch search = new TaskSearch(null, null, null, false, null, from, to);
        TaskQueryService service = new TaskQueryService(repository, access, Clock.fixed(NOW, ZoneId.of("UTC")));
        for (TaskSort sort : TaskSort.values()) service.find(WS, USER, search, sort, 0, 20);
        LocalDate today = LocalDate.of(2026, 1, 14);
        PageRequest page = PageRequest.of(0, 20);
        verify(repository).findNewest(WS, null, null, null, false, null, from, to, today, page);
        verify(repository).findByDueDate(WS, null, null, null, false, null, from, to, today, page);
        verify(repository).findByPriority(WS, null, null, null, false, null, from, to, today, page);
        verify(access, org.mockito.Mockito.times(3)).requireMember(WS, USER);
    }

}
