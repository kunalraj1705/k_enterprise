package com.krb.enterprise.user.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.krb.enterprise.user.domain.User;
import com.krb.enterprise.user.domain.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public User register(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists.");
        }

        String passwordHash = passwordHasher.hash(password);
        User user = User.create(email, passwordHash);
        return userRepository.save(user);
    }

    public Optional<User> findById(UUID userId) {
        return userRepository.findById(userId);
    }

}
