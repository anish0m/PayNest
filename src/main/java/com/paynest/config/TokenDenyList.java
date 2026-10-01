package com.paynest.config;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class TokenDenyList {

    private final Map<String, Instant> denied = new ConcurrentHashMap<>();

    public void deny(String token, Instant expiresAt) {
        evictExpired();
        denied.put(token, expiresAt);
    }

    public boolean isDenied(String token) {
        Instant expiry = denied.get(token);
        if (expiry == null) return false;
        if (expiry.isBefore(Instant.now())) {
            denied.remove(token);
            return false;
        }
        return true;
    }

    private void evictExpired() {
        Instant now = Instant.now();
        denied.values().removeIf(expiry -> expiry.isBefore(now));
    }
}