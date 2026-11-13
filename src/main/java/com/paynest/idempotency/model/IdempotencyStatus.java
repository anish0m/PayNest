package com.paynest.idempotency.model;

/**
 * Lifecycle of a request's idempotency claim.
 *
 * <p>{@link #IN_PROGRESS} prevents a concurrent retry from starting another
 * transfer while the first request is still working. {@link #COMPLETED}
 * means the original result can be replayed. {@link #FAILED} represents a
 * failure that is safe to retry.</p>
 */
public enum IdempotencyStatus {
    /** The first request is still running; a retry must not start another transfer. */
    IN_PROGRESS,

    /** The first request finished; a retry should receive the same result. */
    COMPLETED,

    /** The first request failed safely, so its claim may be released for retry. */
    FAILED
}