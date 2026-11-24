package com.paynest.idempotency.exception;

public final class IdempotencyConflictException extends RuntimeException {

    private final String scope;
    private final String idempotencyKey;

    public IdempotencyConflictException(
            String scope,
            String idempotencyKey) {

        super("Idempotency key is already being processed: "
                + idempotencyKey);

        this.scope = scope;
        this.idempotencyKey = idempotencyKey;
    }

    public String getScope() {
        return scope;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
