package com.example.backend.task.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import com.example.backend.common.exception.DomainValidationException;

class TaskSearchTest {
    @Test
    void supportsOpenEndedAndSingleDayRanges() {
        LocalDate day = LocalDate.of(2026, 2, 28);
        assertThat(search(day, null).dueFrom()).isEqualTo(day);
        assertThat(search(null, day).dueTo()).isEqualTo(day);
        assertThat(search(day, day).dueTo()).isEqualTo(day);
        assertThat(TaskSearch.any().dueFrom()).isNull();
        assertThat(TaskSearch.any().dueTo()).isNull();
    }

    @Test
    void rejectsReversedRanges() {
        assertThatThrownBy(() -> search(LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 1)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Due from must be on or before due to");
    }

    @Test
    void bothBoundsUseTheSameSupportedYearsAsTaskDates() {
        for (int year : new int[] {0, -1, 10000}) {
            LocalDate date = LocalDate.of(year, 1, 1);
            assertThatThrownBy(() -> search(date, null)).isInstanceOf(DomainValidationException.class);
            assertThatThrownBy(() -> search(null, date)).isInstanceOf(DomainValidationException.class);
        }
        assertThat(search(LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)).dueFrom().getYear()).isEqualTo(1);
    }

    private TaskSearch search(LocalDate from, LocalDate to) {
        return new TaskSearch(null, null, null, false, null, from, to);
    }
}
