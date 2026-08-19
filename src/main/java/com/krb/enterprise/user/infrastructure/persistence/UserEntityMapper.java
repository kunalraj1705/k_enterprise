package com.krb.enterprise.user.infrastructure.persistence;

import com.krb.enterprise.user.domain.User;
import com.krb.enterprise.user.domain.UserRole;
import com.krb.enterprise.user.domain.UserStatus;

public final class UserEntityMapper {

    private UserEntityMapper() {
    }

    public static UserEntity toEntity(User user) {
        return new UserEntity(
                user.getId(),
                user.getUserId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus().name(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public static User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getUserId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                UserRole.valueOf(entity.getRole()),
                UserStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}