package com.krb.enterprise.user.domain;

public interface UserIdSequenceRepository {
    long nextValue(UserRole role);
}
