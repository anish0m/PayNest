package com.paynest.user.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.paynest.config.TokenDenyList;
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
    private final TokenDenyList denyList;

    public AuthController(UserService service,
                          JwtService jwtService,
                          TokenDenyList denyList,
                          @Value("${paynest.jwt.expiration-ms}") long expirationMs) {
        this.service = service;
        this.jwtService = jwtService;
        this.denyList = denyList;
        this.expiresInSeconds = expirationMs / 1000;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        User user = service.login(request);
        String token = jwtService.issueToken(user.getEmail());
        return TokenResponse.bearer(token, expiresInSeconds);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            if (jwtService.isValid(token)) {
                denyList.deny(token, jwtService.extractExpiry(token));
            }
        }
    }
}
