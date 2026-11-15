package com.example.backend.workspace.domain;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.example.backend.common.validation.DomainValues;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A shared board. Who may use it is recorded in {@link WorkspaceMember}. */
@Entity
@Table(name = "workspaces")
@EntityListeners(AuditingEntityListener.class)
public class Workspace {

    public static final int NAME_MAX = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX)
    private String name;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    @ColumnDefault("now()")
    private Instant createdAt;

    /** Required by JPA. */
    protected Workspace() {
    }

    public Workspace(String name) {
        rename(name);
    }

    public void rename(String name) {
        this.name = DomainValues.requiredText(name, "Name", NAME_MAX);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
