package com.krb.enterprise.user.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.krb.enterprise.user.domain.User;
import com.krb.enterprise.user.domain.UserRepository;
import com.krb.enterprise.user.domain.UserStatus;

class RegisterUserTest {

    @Test
    void shouldRegisterNewUser() {
        // Test implementation for registering a new user
        FakeUserRepository fakeUserRepository = new FakeUserRepository();
        FakePasswordHasher fakePasswordHasher = new FakePasswordHasher();

        UserService userService = new UserService(fakeUserRepository, fakePasswordHasher);

        User user = userService.register("krb@test.com", "myPassword");
        assertNotNull(user.getId());
        assertEquals("krb@test.com", user.getEmail());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals("hashed-myPassword", user.getPasswordHash());

    }

    @Test
    void shouldRejectDuplicateEmail() {
        FakeUserRepository fakeUserRepository = new FakeUserRepository();
        FakePasswordHasher fakePasswordHasher = new FakePasswordHasher();
        UserService userService = new UserService(fakeUserRepository, fakePasswordHasher);

        // First registration should succeed
        userService.register("krb@test.com", "myPassword");
        assertThrows(IllegalArgumentException.class,
                () -> userService.register("krb@test.com", "myPassword"));
    }

    static class FakeUserRepository implements UserRepository {
        private final Map<UUID, User> users = new HashMap<>();

        @Override
        public boolean existsByEmail(String email) {
            // Simulate that the email does not exist
            return users.values().stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(email));
        }

        @Override
        public User save(User user) {
            users.put(user.getId(), user);
            return user; // Simulate saving the user and returning it
        }

        @Override
        public Optional<User> findById(UUID userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public Optional<User> findByEmail(String email) {
           return users.values().stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
        }
    }

    static class FakePasswordHasher implements PasswordHasher {

        @Override
        public String hash(String rawPassword) {
            return "hashed-" + rawPassword;
        }

        @Override
        public boolean matches(
                String rawPassword,
                String passwordHash) {

            return passwordHash.equals(
                    "hashed-" + rawPassword);
        }
    }

}
