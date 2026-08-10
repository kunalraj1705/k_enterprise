package com.krb.enterprise.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class UserTest {

    @Test
    public void shouldCreateActiveCustomerUser(){
        User user = User.create("krb@test.com", "password123");

        assertNotNull(user.getId());
        assertEquals("krb@test.com", user.getEmail());
        assertEquals("password123", user.getPasswordHash());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(UserRole.CUSTOMER, user.getRole());
        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
    }

    @Test
    public void shouldSuspendActiveUser() {
        User user = User.create("krb@test.com", "password123");
        user.suspend();

        assertEquals(UserStatus.SUSPENDED, user.getStatus());
    }

    @Test
    public void shouldActivateSuspendedUser() {
        User user = User.create("krb@test.com", "password123");
        user.suspend();
        user.activate();

        assertEquals(UserStatus.ACTIVE, user.getStatus());
    }

    @Test
    public void shouldNotSuspendAlreadySuspendedUser() {
        User user = User.create("krb@test.com", "password123");
        user.suspend();
        assertThrows(IllegalStateException.class, user::suspend);
    }

    @Test
    public void shouldNotActivateAlreadyActiveUser() {
        User user = User.create("krb@test.com", "password123");
        assertThrows(IllegalStateException.class, user::activate);
    }
}
