package com.krb.enterprise.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterCustomerRequest(
        @NotBlank @Email String email,

        @NotBlank String password) {

}
