package com.example.backend.task.domain;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.ColumnDefault;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.example.backend.common.exception.ConflictException;
import com.example.backend.common.exception.DomainValidationException;
import com.example.backend.common.validation.DomainValues;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Task aggregate. State changes go through domain methods so invariants
 * (non-blank title, non-null status/priority) are enforced in one place.
 *
 * <p>Other aggregates (workspace, users) are referenced by id, not mapped as
 * associations: the task module never loads them, and the database enforces
 * the references (including "the assignee is a member of this workspace").
 */
@Entity
@Table(name = "tasks")
@EntityListeners(AuditingEntityListener.class)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    private LocalDate dueDate;

    /** A member of {@link #workspaceId}, or null. */
    private Long assigneeId;

    @Column(updatable = false)
    private Long createdBy;

    private Long updatedBy;

    /** Optimistic-locking version; incremented by Hibernate on every update. */
    @Version
    @Column(nullable = false)
    @ColumnDefault("0")
    private long version;

    /** Set by JPA auditing from the application Clock (see TimeConfig). */
    @CreatedDate
    @Column(nullable = false, updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    @ColumnDefault("now()")
    private Instant updatedAt;

    /** Required by JPA. */
    protected Task() {
    }

    public Task(Long workspaceId, Long createdBy, String title, String description, TaskStatus status,
                TaskPriority priority, LocalDate dueDate) {
        this.workspaceId = required(workspaceId, "Workspace");
        this.createdBy = required(createdBy, "Creator");
        this.updatedBy = createdBy;
        updateDetails(title, description, priority, dueDate);
        changeStatus(status);
    }

    /** Records who made the latest change; call once per command. */
    public void changedBy(Long userId) {
        this.updatedBy = required(userId, "User");
    }

    public boolean wasCreatedBy(Long userId) {
        return createdBy != null && createdBy.equals(userId);
    }

    public void updateDetails(String title, String description, TaskPriority priority, LocalDate dueDate) {
        String validTitle = DomainValues.requiredText(title, "Title", 120);
        String validDescription = DomainValues.optionalText(description, "Description", 1000);
        TaskPriority validPriority = required(priority, "Priority");
        LocalDate validDueDate = DomainValues.optionalDate(dueDate, "Due date");
        this.title = validTitle;
        this.description = validDescription;
        this.priority = validPriority;
        this.dueDate = validDueDate;
    }

    public void changeStatus(TaskStatus status) {
        this.status = required(status, "Status");
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new DomainValidationException(field + " is required");
        }
        return value;
    }

    /**
     * Guards against lost updates: a client editing a stale copy is rejected
     * instead of silently overwriting someone else's changes.
     */
    public void verifyVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new ConflictException("Task was modified by someone else. Reload and try again.");
        }
    }

    /**
     * @param userId the new assignee, or null to unassign. The caller checks
     *               workspace membership; the database enforces it as well.
     */
    public void assignTo(Long userId) {
        this.assigneeId = userId;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
