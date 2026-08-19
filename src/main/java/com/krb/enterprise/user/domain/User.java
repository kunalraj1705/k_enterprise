package com.krb.enterprise.user.domain;

import java.time.Instant;
import java.util.UUID;

public class User {
    private final UUID id;
    private final String userId;
    private final String email;
    private final String passwordHash;
    private UserRole role;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(UUID id, String userId, String email, String passwordHash, UserRole role, UserStatus status,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static User create(String userId, String email, String passwordHash, UserRole role) {
        Instant now = Instant.now();
        return new User(UUID.randomUUID(), userId, email, passwordHash, role, UserStatus.ACTIVE, now, now);
    }

    public void suspend() {
        if (this.status == UserStatus.SUSPENDED) {
            throw new IllegalStateException("User is already suspended.");
        }
        status = UserStatus.SUSPENDED;
        updatedAt = Instant.now();
    }

    public void activate() {
        if (this.status == UserStatus.ACTIVE) {
            throw new IllegalStateException("User is already active.");
        }
        status = UserStatus.ACTIVE;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUserId() {
        return userId;
    }

}
