package com.paynest.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paynest.config.JwtService;
import com.paynest.user.dto.LoginRequest;
import com.paynest.user.dto.TokenResponse;
import com.paynest.user.model.User;
import com.paynest.user.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService service;
    private final JwtService jwtService;
    private final long expiresInSeconds;

    public AuthController(UserService service,
                          JwtService jwtService,
                          @Value("${paynest.jwt.expiration-ms}") long expirationMs) {
        this.service = service;
        this.jwtService = jwtService;
        this.expiresInSeconds = expirationMs / 1000;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = service.login(request);
        String token = jwtService.issueToken(user.getEmail());
        return TokenResponse.bearer(token, expiresInSeconds);
    }
}
