package com.paynest.idempotency.repository;

import com.paynest.idempotency.model.IdempotencyRecord;
import com.paynest.idempotency.model.IdempotencyStatus;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.time.OffsetDateTime;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"jpa", "jdbc"})
public class JdbcIdempotencyRepository implements IdempotencyRepository {

    private static final String COLUMNS =
            "scope, idempotency_key, status, transfer_id, request_fingerprint, created_at";

    private final JdbcClient jdbc;

    public JdbcIdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> claim(IdempotencyRecord attempt) {
        while (true) {
            int rows = jdbc.sql("""
                                INSERT INTO idempotency_keys
                                    (scope, idempotency_key, status, request_fingerprint)
                                VALUES (?, ?, ?, ?)
                                ON CONFLICT DO NOTHING
                                """)
                    .params(
                            attempt.scope(),
                            attempt.idempotencyKey(),
                            attempt.status().name(),
                            attempt.requestFingerprint())
                    .update();

            if (rows == 1) {
                return Optional.empty();
            }

            Optional<IdempotencyRecord> existing =
                    find(attempt.scope(), attempt.idempotencyKey());

            if (existing.isPresent()) {
                return existing;
            }

            // The conflicting owner released its in-progress claim before our
            // lookup. Retry the atomic INSERT; we must not signal success yet.
        }
    }

    @Override
    public int settle(String scope, String idempotencyKey, UUID transferId) {
        return jdbc.sql("""
                        UPDATE idempotency_keys
                           SET status = 'COMPLETED',
                               transfer_id = ?
                         WHERE scope = ?
                           AND idempotency_key = ?
                           AND status = 'IN_PROGRESS'
                        """)
                .params(transferId, scope, idempotencyKey)
                .update();
    }

    @Override
    public int release(String scope, String idempotencyKey) {
        return jdbc.sql("""
                        DELETE FROM idempotency_keys
                         WHERE scope = ?
                           AND idempotency_key = ?
                           AND status = 'IN_PROGRESS'
                        """)
                .params(scope, idempotencyKey)
                .update();
    }

    @Override
    public Optional<IdempotencyRecord> find(String scope, String idempotencyKey) {
        return jdbc.sql("""
                SELECT\s""" + COLUMNS + """
                  FROM idempotency_keys
                 WHERE scope = ?
                   AND idempotency_key = ?
                """)
                .params(scope, idempotencyKey)
                .query(JdbcIdempotencyRepository::mapRow)
                .optional();
    }

    private static IdempotencyRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new IdempotencyRecord(
                rs.getString("scope"),
                rs.getString("idempotency_key"),
                IdempotencyStatus.valueOf(rs.getString("status")),
                rs.getObject("transfer_id", UUID.class),
                rs.getString("request_fingerprint"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
