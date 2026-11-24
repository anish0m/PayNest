package com.paynest.idempotency.model;

import java.time.Instant;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable value representing a row in {@code idempotency_keys}.
 *
 * <p>{@code transferId} is null while the claim is in progress.</p>
 */
public record IdempotencyRecord(
        String scope,
        String idempotencyKey,
        IdempotencyStatus status,
        UUID transferId,
        String requestFingerprint,
        Instant createdAt
) {
    public IdempotencyRecord {
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint cannot be null");
    }
}