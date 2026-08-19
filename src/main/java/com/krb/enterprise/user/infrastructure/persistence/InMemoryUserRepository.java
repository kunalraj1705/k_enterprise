package com.krb.enterprise.user.infrastructure.persistence;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//import org.springframework.stereotype.Repository;

import com.krb.enterprise.user.domain.User;
import com.krb.enterprise.user.domain.UserRepository;

//@Repository //: This annotation is commented out to indicate that this class is not currently being used as a Spring-managed bean. It can be uncommented if you want to use this in-memory repository in your application context.
public class InMemoryUserRepository implements UserRepository {

    private final Map<UUID, User> users = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        users.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(UUID userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.values().stream().anyMatch(user -> user.getEmail().equals(email));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.values().stream().filter(user -> user.getEmail().equals(email)).findFirst();
    }

    @Override
    public Optional<User> findByUserId(String userId) {
        return users.values().stream().filter(user -> user.getUserId().equals(userId)).findFirst();
    }

}
