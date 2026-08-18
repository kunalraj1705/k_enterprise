package com.krb.enterprise.authentication.datamodel;

public record LoginResponse(
                String accessToken,
                String tokenType) {
}