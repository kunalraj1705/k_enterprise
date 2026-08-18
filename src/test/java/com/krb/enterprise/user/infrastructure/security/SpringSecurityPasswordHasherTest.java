package com.krb.enterprise.user.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.krb.enterprise.security.authentication.SpringSecurityPasswordHasher;

public class SpringSecurityPasswordHasherTest {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final SpringSecurityPasswordHasher passwordHasher = new SpringSecurityPasswordHasher(passwordEncoder);

    @Test
    void shouldHashPassword() {
        String rawPassword = "password123";
        String hashedPassword = passwordHasher.hash(rawPassword);
        assertNotNull(hashedPassword);
        assertNotEquals(rawPassword, hashedPassword);
    }

    @Test
    void shouldMatchCorrectPassword() {
        String rawPassword = "password123";
        String hashedPassword = passwordHasher.hash(rawPassword);
        assertTrue(passwordHasher.matches(rawPassword, hashedPassword));
    }

    @Test
    void shouldRejectWrongPassword() {
        String rawPassword = "password123";
        String wrongPassword = "wrongpassword";
        String hashedPassword = passwordHasher.hash(rawPassword);
        assertFalse(passwordHasher.matches(wrongPassword, hashedPassword));
    }

    @Test
    void shouldGenerateDifferentHashesForSamePassword() {
        String rawPassword = "password123";
        String hashedPassword1 = passwordHasher.hash(rawPassword);
        String hashedPassword2 = passwordHasher.hash(rawPassword);
        assertNotEquals(hashedPassword1, hashedPassword2);
    }

}
