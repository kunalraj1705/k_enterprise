package com.krb.enterprise.user.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.krb.enterprise.common.exception.ApplicationException;
import com.krb.enterprise.user.domain.User;
import com.krb.enterprise.user.domain.UserRepository;
import com.krb.enterprise.user.domain.UserRole;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final UserIdGenerator userIdGenerator;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher, UserIdGenerator userIdGenerator) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.userIdGenerator = userIdGenerator;
    }

    public User registerCustomer(String email, String password) {
        return register(email, password, UserRole.CUSTOMER);
    }

    public User register(String email, String password, UserRole userRole) {
        if (userRepository.existsByEmail(email)) {
            throw new ApplicationException(HttpStatus.CONFLICT, "Email already exists.");
        }

        String passwordHash = passwordHasher.hash(password);

        String userId = userIdGenerator.generate(userRole);
        User user = User.create(userId, email, passwordHash, userRole);
        return userRepository.save(user);
    }

    public Optional<User> findById(UUID userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findByUserId(String userId) {
        return userRepository.findByUserId(userId);
    }

}
