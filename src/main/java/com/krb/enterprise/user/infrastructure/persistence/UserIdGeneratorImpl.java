package com.krb.enterprise.user.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.krb.enterprise.user.application.UserIdGenerator;
import com.krb.enterprise.user.domain.UserIdSequenceRepository;
import com.krb.enterprise.user.domain.UserRole;

@Component
public class UserIdGeneratorImpl implements UserIdGenerator {

    private final UserIdSequenceRepository sequenceRepository;

    public UserIdGeneratorImpl(
            UserIdSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    @Override
    public String generate(UserRole role) {

        String prefix = switch (role) {
            case CUSTOMER -> "U";
            case ADMIN -> "A";
            case OPERATIONS -> "E";
        };

        long sequence = sequenceRepository.nextValue(role);

        return String.format("%s%07d", prefix, sequence);
    }
}