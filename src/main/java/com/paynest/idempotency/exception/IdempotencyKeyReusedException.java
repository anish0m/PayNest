package com.paynest.idempotency.exception;

public final class IdempotencyKeyReusedException extends RuntimeException {

    private final String scope;
    private final String idempotencyKey;

    public IdempotencyKeyReusedException(
            String scope,
            String idempotencyKey) {

        super("Idempotency key was reused for a different request: "
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
