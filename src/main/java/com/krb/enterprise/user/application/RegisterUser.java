package com.krb.enterprise.user.application;

import org.springframework.stereotype.Service;

import com.krb.enterprise.user.domain.User;
import com.krb.enterprise.user.domain.UserRepository;

@Service
public class RegisterUser {

    private final UserRepository userRepository;
     private final PasswordHasher passwordHasher;

    public RegisterUser(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public User execute(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists.");
        }

        String passwordHash = passwordHasher.hash(password);
        User user = User.create(email, passwordHash);
        return userRepository.save(user);
    }
    

}
