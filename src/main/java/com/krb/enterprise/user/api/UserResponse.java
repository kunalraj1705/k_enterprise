package com.krb.enterprise.user.api;

import java.util.UUID;

import com.krb.enterprise.user.domain.User;
import com.krb.enterprise.user.domain.UserRole;
import com.krb.enterprise.user.domain.UserStatus;

public record UserResponse(UUID id, String userId, String email, UserStatus status, UserRole role) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUserId(),
                user.getEmail(),
                user.getStatus(),
                user.getRole()
        );
    }

}
