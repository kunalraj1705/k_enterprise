package com.krb.enterprise.authentication.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.krb.enterprise.authentication.datamodel.LoginRequest;
import com.krb.enterprise.authentication.datamodel.LoginResponse;
import com.krb.enterprise.authentication.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        String accessToken = authService.login(request.email(), request.password());

        return ResponseEntity.ok(
                new LoginResponse(
                        accessToken,
                        "Bearer"));
    }
}