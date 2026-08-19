package com.krb.enterprise.user.application;

import com.krb.enterprise.user.domain.UserRole;

public interface UserIdGenerator {
    String generate(UserRole role);
}
