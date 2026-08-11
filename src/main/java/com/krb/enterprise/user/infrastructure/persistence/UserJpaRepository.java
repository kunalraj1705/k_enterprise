package com.krb.enterprise.user.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserJpaRepository
        extends JpaRepository<UserEntity, UUID> {

    boolean existsByEmail(String email);
}