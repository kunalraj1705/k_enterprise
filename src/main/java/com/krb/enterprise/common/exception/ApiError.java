package com.krb.enterprise.common.exception;

public record ApiError(
        int status,
        String error,
        String message) {
}
