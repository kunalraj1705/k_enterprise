    package com.krb.enterprise.user.domain;

public enum UserRole {
    CUSTOMER("CUSTOMER"),
    ADMIN("ADMIN"),
    OPERATIONS("OPERATIONS");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    public static UserRole fromString(String role) {
        for (UserRole userRole : UserRole.values()) {
            if (userRole.name().equalsIgnoreCase(role)) {
                return userRole;
            }
        }
        throw new IllegalArgumentException("Invalid user role: " + role);
    }
}
