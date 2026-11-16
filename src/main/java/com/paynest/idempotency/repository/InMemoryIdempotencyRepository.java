package com.paynest.idempotency.repository;

import com.paynest.idempotency.model.IdempotencyRecord;
import com.paynest.idempotency.model.IdempotencyStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * Test-only implementation of the idempotency repository.
 *
 * <p>Passing tests against this repository do not prove database behaviour:
 *
 * <ul>
 *   <li>conflicts across database connections, transactions, or processes;
 *   <li>that a claim commits before the transfer work starts;
 *   <li>the {@code transfer_id} foreign-key constraint;
 *   <li>the database status CHECK constraint; or
 *   <li>that the JDBC SQL is valid and executable.
 * </ul>
 *
 * <p>Those properties require integration tests against Postgres.
 */
@Repository
@Profile("test")
public class InMemoryIdempotencyRepository implements IdempotencyRepository {

    private final Map<Key, IdempotencyRecord> records =
            new ConcurrentHashMap<>();

    private record Key(String scope, String idempotencyKey) {
    }

    private Key key(String scope, String idempotencyKey) {
        return new Key(scope, idempotencyKey);
    }

    @Override
    public Optional<IdempotencyRecord> claim(IdempotencyRecord attempt) {
        IdempotencyRecord stored = new IdempotencyRecord(
                attempt.scope(),
                attempt.idempotencyKey(),
                attempt.status(),
                attempt.transferId(),
                attempt.requestFingerprint(),
                Instant.now());

        return Optional.ofNullable(
                records.putIfAbsent(
                        key(attempt.scope(), attempt.idempotencyKey()),
                        stored));
    }

    @Override
    public int settle(
            String scope,
            String idempotencyKey,
            UUID transferId) {

        Key key = key(scope, idempotencyKey);

        while (true) {
            IdempotencyRecord existing = records.get(key);

            if (existing == null
                    || existing.status() != IdempotencyStatus.IN_PROGRESS) {
                return 0;
            }

            IdempotencyRecord completed = new IdempotencyRecord(
                    existing.scope(),
                    existing.idempotencyKey(),
                    IdempotencyStatus.COMPLETED,
                    transferId,
                    existing.requestFingerprint(),
                    existing.createdAt());

            if (records.replace(key, existing, completed)) {
                return 1;
            }
        }
    }

    @Override
    public int release(String scope, String idempotencyKey) {
        Key key = key(scope, idempotencyKey);

        while (true) {
            IdempotencyRecord existing = records.get(key);

            if (existing == null
                    || existing.status() != IdempotencyStatus.IN_PROGRESS) {
                return 0;
            }

            if (records.remove(key, existing)) {
                return 1;
            }
        }
    }

    @Override
    public Optional<IdempotencyRecord> find(
            String scope,
            String idempotencyKey) {

        return Optional.ofNullable(
                records.get(key(scope, idempotencyKey)));
    }
}
