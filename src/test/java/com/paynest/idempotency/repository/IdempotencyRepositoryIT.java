package com.paynest.idempotency.repository;

import com.paynest.idempotency.model.IdempotencyRecord;
import com.paynest.idempotency.model.IdempotencyStatus;

import com.paynest.transfer.service.TransferService;

import com.paynest.user.model.User;
import com.paynest.user.repository.UserRepository;

import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.SpringDataWalletRepository;

import java.math.BigDecimal;

import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"jpa", "jpa-it"})
class IdempotencyRepositoryIT {

    @Autowired
    private IdempotencyRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private TransferService transfers;

    @Autowired
    private SpringDataWalletRepository wallets;

    @Autowired
    private UserRepository users;

    @BeforeEach
    void clean() {
        deleteAllRows();
    }

    @AfterEach
    void cleanAfterTest() {
        deleteAllRows();
    }

    private void deleteAllRows() {
        jdbc.sql("DELETE FROM idempotency_keys").update();
        jdbc.sql("DELETE FROM transfers").update();
        jdbc.sql("DELETE FROM wallets").update();
        jdbc.sql("DELETE FROM users").update();
    }

    /**
     * The returned wallet's balance is stale because the raw JDBC UPDATE occurs
     * behind Hibernate's back. Use its ID only; query JdbcClient for balances.
     */
    private Wallet fundedWallet(String email, String amount) {
        User user = users.save(
                new User("Transfer", "User", email, "Pass1234#"));

        Wallet wallet = wallets.saveAndFlush(new Wallet(user));

        jdbc.sql("UPDATE wallets SET balance = ? WHERE id = ?")
                .param(new BigDecimal(amount))
                .param(wallet.getId())
                .update();

        return wallet;
    }

    @Test
    void onlyOneOfSixteenSimultaneousClaimsSucceeds() throws Exception {
        String scope = "transfer:race";
        String key = "claim-race-key";
        String fingerprint = "1:2:10.00";

        CountDownLatch allReady = new CountDownLatch(16);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch allFinished = new CountDownLatch(16);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> throwables =
                new ConcurrentLinkedQueue<>();

        ExecutorService executor = Executors.newFixedThreadPool(16);

        try {
            for (int i = 0; i < 16; i++) {
                executor.submit(() -> {
                    try {
                        IdempotencyRecord attempt = new IdempotencyRecord(
                                scope,
                                key,
                                IdempotencyStatus.IN_PROGRESS,
                                null,
                                fingerprint,
                                Instant.now());

                        allReady.countDown();

                        if (!go.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError(
                                    "worker did not receive starting signal");
                        }

                        Optional<IdempotencyRecord> existing = tx.execute(
                                status -> repository.claim(attempt));

                        if (existing.isEmpty()) {
                            successes.incrementAndGet();
                        } else {
                            conflicts.incrementAndGet();
                        }
                    } catch (Throwable throwable) {
                        throwables.add(throwable);
                    } finally {
                        allFinished.countDown();
                    }
                });
            }

            assertTrue(
                    allReady.await(10, TimeUnit.SECONDS),
                    "all sixteen workers should be ready");

            go.countDown();

            assertTrue(
                    allFinished.await(20, TimeUnit.SECONDS),
                    "all sixteen workers should finish");
        } finally {
            go.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertTrue(
                throwables.isEmpty(),
                () -> "worker failures: " + throwables);

        assertEquals(1, successes.get());
        assertEquals(15, conflicts.get());

        Long rows = jdbc.sql("""
                        SELECT COUNT(*)
                          FROM idempotency_keys
                         WHERE scope = ?
                           AND idempotency_key = ?
                        """)
                .params(scope, key)
                .query(Long.class)
                .single();

        String status = jdbc.sql("""
                        SELECT status
                          FROM idempotency_keys
                         WHERE scope = ?
                           AND idempotency_key = ?
                        """)
                .params(scope, key)
                .query(String.class)
                .single();

        assertEquals(1L, rows);
        assertEquals("IN_PROGRESS", status);
    }

    @Test
    void settleRecordsTheTransferAndIsNotRepeatable() {
        Wallet sender = fundedWallet("settle-sender@example.com", "100.00");
        Wallet receiver = fundedWallet("settle-receiver@example.com", "0.00");

        UUID firstTransferId = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("10.00")).getId();

        UUID secondTransferId = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("10.00")).getId();

        String scope = "transfer:" + sender.getId();
        String key = "settle-key";

        assertTrue(repository.claim(new IdempotencyRecord(
                scope,
                key,
                IdempotencyStatus.IN_PROGRESS,
                null,
                "settle-fingerprint",
                Instant.now())).isEmpty());

        assertEquals(1, repository.settle(scope, key, firstTransferId));
        assertEquals(0, repository.settle(scope, key, secondTransferId));

        UUID storedTransferId = jdbc.sql("""
                        SELECT transfer_id
                          FROM idempotency_keys
                         WHERE scope = ?
                           AND idempotency_key = ?
                        """)
                .params(scope, key)
                .query(UUID.class)
                .single();

        assertEquals(firstTransferId, storedTransferId);
    }

    @Test
    void releaseDeletesTheClaimAndIsIdempotent() {
        String scope = "transfer:release";
        String key = "release-key";
        String fingerprint = "1:2:10.00";

        assertTrue(repository.claim(new IdempotencyRecord(
                scope,
                key,
                IdempotencyStatus.IN_PROGRESS,
                null,
                fingerprint,
                Instant.now())).isEmpty());

        assertEquals(1, repository.release(scope, key));
        assertEquals(0, repository.release(scope, key));

        assertTrue(repository.claim(new IdempotencyRecord(
                scope,
                key,
                IdempotencyStatus.IN_PROGRESS,
                null,
                fingerprint,
                Instant.now())).isEmpty());
    }

    @Test
    void theseTestsRunAgainstTheRealDatabaseNotTheFake() {
        assertInstanceOf(
                JdbcIdempotencyRepository.class,
                repository,
                () -> "Expected JdbcIdempotencyRepository but got "
                        + repository.getClass().getName());

        String database = jdbc.sql("SELECT current_database()")
                .query(String.class)
                .single();

        assertEquals("paynest_test", database);
    }

    @Test
    void findReturnsEveryFieldItStored() {
        String scope = "transfer:find";
        String key = "find-key";
        String fingerprint = "1:2:10.00";

        assertTrue(repository.claim(new IdempotencyRecord(
                scope,
                key,
                IdempotencyStatus.IN_PROGRESS,
                null,
                fingerprint,
                Instant.now())).isEmpty());

        IdempotencyRecord found = repository.find(scope, key).orElseThrow();

        assertEquals(scope, found.scope());
        assertEquals(key, found.idempotencyKey());
        assertEquals(IdempotencyStatus.IN_PROGRESS, found.status());
        assertNull(found.transferId());
        assertEquals(fingerprint, found.requestFingerprint());
        assertNotNull(found.createdAt());

        assertTrue(repository.find(scope, "missing-key").isEmpty());
    }

}