package com.krb.enterprise.user.api;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.krb.enterprise.common.exception.ApplicationException;
import com.krb.enterprise.user.application.UserService;
import com.krb.enterprise.user.domain.User;
import com.krb.enterprise.user.domain.UserRole;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/user")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/customer")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerCustomer(@Valid @RequestBody RegisterCustomerRequest request) {
        User user = userService.registerCustomer(request.email(), request.password());
        return UserResponse.from(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse registerUser(@Valid @RequestBody RegisterUserRequest request) {
        User user = userService.register(request.email(), request.password(), request.userRole());
        return UserResponse.from(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/id/{uuid}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID uuid) {
        return userService.findById(uuid)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + uuid));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATIONS') or (hasRole('CUSTOMER') and authentication.name == #userId)")
    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUser(@PathVariable String userId) {
        return userService.findByUserId(userId)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + userId));
    }

    @GetMapping
    public ResponseEntity<UserResponse> getUserMe(Authentication authentication) {
        String userId = authentication.getName();

        return userService.findByUserId(userId)
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + userId));
    }
}
