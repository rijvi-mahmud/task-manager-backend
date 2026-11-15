package com.example.backend.workspace.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import java.util.Objects;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * A user's access to a workspace. Aggregates reference each other by id
 * (workspace, user) rather than by object, so modules stay independent.
 */
@Entity
@Table(name = "workspace_members")
@IdClass(WorkspaceMember.Key.class)
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceMember {

    @Id
    private Long workspaceId;

    @Id
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    @ColumnDefault("now()")
    private Instant joinedAt;

    /** Required by JPA. */
    protected WorkspaceMember() {
    }

    public WorkspaceMember(Long workspaceId, Long userId, WorkspaceRole role) {
        this.workspaceId = Objects.requireNonNull(workspaceId);
        this.userId = Objects.requireNonNull(userId);
        this.role = Objects.requireNonNull(role);
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public Long getUserId() {
        return userId;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public boolean isOwner() {
        return role == WorkspaceRole.OWNER;
    }

    /** Composite primary key (workspace_id, user_id). */
    public static class Key implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private Long workspaceId;
        private Long userId;

        /** Required by JPA. */
        protected Key() {
        }

        public Key(Long workspaceId, Long userId) {
            this.workspaceId = workspaceId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(workspaceId, key.workspaceId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(workspaceId, userId);
        }
    }
}
