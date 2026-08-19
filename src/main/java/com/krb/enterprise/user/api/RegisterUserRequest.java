package com.krb.enterprise.user.api;

import com.krb.enterprise.user.domain.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterUserRequest(
        @NotBlank @Email String email,

        @NotBlank String password,

        @NotNull UserRole userRole) {

}
