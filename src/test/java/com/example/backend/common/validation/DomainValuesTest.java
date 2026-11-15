package com.example.backend.common.validation;

import com.example.backend.common.exception.DomainValidationException;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.example.backend.identity.domain.User;
import com.example.backend.task.domain.Task;
import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;

class DomainValuesTest {
    @Test
    void commandsCannotCreateOversizedTaskFields() {
        assertThatThrownBy(() -> new Task(1L, 1L, "x".repeat(121), null, TaskStatus.TODO, TaskPriority.LOW, null))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new Task(1L, 1L, "title", "x".repeat(1001), TaskStatus.TODO, TaskPriority.LOW, null))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void invalidUpdateLeavesAggregateUnchanged() {
        Task task = new Task(1L, 1L, "original", null, TaskStatus.TODO, TaskPriority.LOW, null);
        assertThatThrownBy(() -> task.updateDetails("changed", "x".repeat(1001), TaskPriority.HIGH, null))
                .isInstanceOf(DomainValidationException.class);
        assertThat(task.getTitle()).isEqualTo("original");
        assertThat(task.getPriority()).isEqualTo(TaskPriority.LOW);
    }

    @Test
    void userRulesAlsoApplyOutsideHttp() {
        assertThatThrownBy(() -> User.registered("m@example.com", "x".repeat(81), "{noop}x"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> User.pending("not-an-email"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> User.pending("a b@example.com"))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> User.pending("a".repeat(110) + "@example.com"))
                .isInstanceOf(DomainValidationException.class);
        assertThat(User.registered("MEMBER@EXAMPLE.COM", " Member ", "{noop}x").getEmail()).isEqualTo("member@example.com");
    }

    @Test
    void pendingUsersGetANameFromTheirEmailAndCanBeClaimedOnce() {
        User user = User.pending("Grace.Hopper@Example.com");
        assertThat(user.getDisplayName()).isEqualTo("grace.hopper");
        assertThat(user.isRegistered()).isFalse();
        user.claim("Grace Hopper", "{noop}secret");
        assertThat(user.isRegistered()).isTrue();
        assertThatThrownBy(() -> user.claim("Someone else", "{noop}other"))
                .isInstanceOf(com.example.backend.common.exception.ConflictException.class);
    }
    @Test
    void dueDatesUseTheSameRangeAsTheClient() {
        for (int year : new int[] {0, -1, 10000}) {
            assertThatThrownBy(() -> new Task(1L, 1L, "date", null, TaskStatus.TODO, TaskPriority.LOW,
                    java.time.LocalDate.of(year, 1, 1))).isInstanceOf(DomainValidationException.class);
        }
        for (int year : new int[] {1, 9999}) {
            assertThat(new Task(1L, 1L, "date", null, TaskStatus.TODO, TaskPriority.LOW,
                    java.time.LocalDate.of(year, 1, 1)).getDueDate().getYear()).isEqualTo(year);
        }
    }

}
