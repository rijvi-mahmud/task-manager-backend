package com.example.backend.task.query;

import com.example.backend.common.exception.DomainValidationException;
import com.example.backend.common.validation.DomainValues;
import java.time.LocalDate;
import com.example.backend.task.domain.TaskPriority;
import com.example.backend.task.domain.TaskStatus;

/**
 * Task list criteria. Null means "any".
 *
 * @param assigneeId only tasks assigned to this member
 * @param unassigned only tasks with no assignee; cannot be combined with assigneeId
 * @param text       case-insensitive substring of the title or description
 * @param dueFrom    inclusive lower due-date bound; null leaves it open
 * @param dueTo      inclusive upper due-date bound; null leaves it open
 */
public record TaskSearch(TaskStatus status, TaskPriority priority, Long assigneeId, boolean unassigned, String text,
                         LocalDate dueFrom, LocalDate dueTo) {

    public TaskSearch {
        if (unassigned && assigneeId != null) {
            throw new DomainValidationException("Use either assigneeId or unassigned, not both");
        }
        dueFrom = DomainValues.optionalDate(dueFrom, "Due from");
        dueTo = DomainValues.optionalDate(dueTo, "Due to");
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw new DomainValidationException("Due from must be on or before due to");
        }
        text = text == null || text.isBlank() ? null : text.strip();
    }

    /** Criteria without a due-date restriction. */
    public TaskSearch(TaskStatus status, TaskPriority priority, Long assigneeId, boolean unassigned, String text) {
        this(status, priority, assigneeId, unassigned, text, null, null);
    }

    public static TaskSearch any() {
        return new TaskSearch(null, null, null, false, null);
    }

    /**
     * LIKE pattern for {@link #text}, lower-cased, with the wildcard characters
     * escaped (escape character {@code !}) so a search for "50%" means the literal text.
     */
    String likePattern() {
        if (text == null) {
            return null;
        }
        String escaped = text.toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
