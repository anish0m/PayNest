package com.paynest.idempotency.repository;

import com.paynest.idempotency.model.IdempotencyRecord;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for atomic idempotency claims and their results.
 */
public interface IdempotencyRepository {

    /**
     * Returns empty when the claim succeeds, or the existing record when it
     * conflicts. The claim decision must be made atomically by one database
     * statement; implementations must not SELECT and then INSERT.
     *
     * @param attempt the new in-progress claim to attempt
     * @return empty if this caller owns the claim, otherwise the existing record
     */
    Optional<IdempotencyRecord> claim(IdempotencyRecord attempt);

    /**
     * Changes an in-progress claim to completed and records its transfer.
     *
     * @return the number of rows changed: normally 1, or 0 if no in-progress
     *         claim matched
     */
    int settle(String scope, String idempotencyKey, UUID transferId);

    /**
     * Deletes an in-progress claim, allowing a safe retry to claim the key.
     * This intentionally leaves no failed-attempt row behind; deleting it lets
     * the normal insert-on-conflict claim path work without special handling.
     *
     * @return the number of rows deleted: normally 1, or 0 if no in-progress
     *         claim matched
     */
    int release(String scope, String idempotencyKey);

    /**
     * Finds a stored record for replay or assertions. This is not the claim
     * path: using {@code find} to decide whether to insert would reintroduce
     * the race that {@link #claim(IdempotencyRecord)} prevents.
     */
    Optional<IdempotencyRecord> find(String scope, String idempotencyKey);
}
