package com.krb.enterprise.user.api;

public record LoginResponse(
                String accessToken,
                String tokenType) {
}