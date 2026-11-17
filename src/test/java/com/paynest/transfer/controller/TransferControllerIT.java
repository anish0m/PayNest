package com.paynest.transfer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.paynest.config.JwtService;

import com.paynest.idempotency.model.IdempotencyRecord;
import com.paynest.idempotency.model.IdempotencyStatus;
import com.paynest.idempotency.repository.IdempotencyRepository;
import com.paynest.idempotency.service.IdempotencyService;

import com.paynest.user.model.User;
import com.paynest.user.repository.UserRepository;

import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.JpaWalletRepository;
import com.paynest.wallet.repository.SpringDataWalletRepository;
import com.paynest.wallet.repository.WalletRepository;

import java.math.BigDecimal;

import java.time.Instant;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"jpa", "jpa-it"})
@Import(TransferControllerIT.ConflictEveryAttemptConfig.class)
class TransferControllerIT {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SpringDataWalletRepository wallets;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @BeforeEach
    void clean() {
        deleteAllRows();
    }

    @AfterEach
    void cleanAfterTest() {
        ConflictEveryAttemptConfig.disarm();
        deleteAllRows();
    }

    private void deleteAllRows() {
        jdbc.sql("DELETE FROM idempotency_keys").update();
        jdbc.sql("DELETE FROM transfers").update();
        jdbc.sql("DELETE FROM wallets").update();
        jdbc.sql("DELETE FROM users").update();
    }

    private Wallet fundedWallet(String email, String amount) {
        User user = users.save(
                new User("Transfer", "User", email, "unused-password-hash"));
        Wallet wallet = wallets.saveAndFlush(new Wallet(user));

        jdbc.sql("UPDATE wallets SET balance = ? WHERE id = ?")
                .param(new BigDecimal(amount))
                .param(wallet.getId())
                .update();

        return wallet;
    }

    private HttpResponse<String> post(
            String email,
            String requestBody,
            String idempotencyKey) throws Exception {

        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/transfers"))
                .header("Authorization", "Bearer " + jwtService.issueToken(email))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));

        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }

        return HttpClient.newHttpClient().send(
                request.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private long transferCount() {
        return jdbc.sql("SELECT COUNT(*) FROM transfers")
                .query(Long.class)
                .single();
    }

    private long idempotencyKeyCount() {
        return jdbc.sql("SELECT COUNT(*) FROM idempotency_keys")
                .query(Long.class)
                .single();
    }

    private BigDecimal walletBalance(Long walletId) {
        return jdbc.sql("SELECT balance FROM wallets WHERE id = ?")
                .param(walletId)
                .query(BigDecimal.class)
                .single();
    }

    @Test
    void exhaustedOptimisticRetriesReturnSanitizedRetryableConflict() throws Exception {
        String senderEmail = "controller-retry-sender@example.com";
        Wallet sender = fundedWallet(senderEmail, "100.00");
        Wallet receiver = fundedWallet("controller-retry-receiver@example.com", "0.00");
        ConflictEveryAttemptConfig.armConflictOn(receiver.getId());

        String requestBody = """
                {
                  "receiverWalletId": %d,
                  "amount": 5.00
                }
                """.formatted(receiver.getId());

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/transfers"))
                .header("Authorization", "Bearer " + jwtService.issueToken(senderEmail))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        HttpResponse<String> result = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(409, result.statusCode());
        String response = result.body();
        assertTrue(response != null && !response.isBlank());

        JsonNode problem = new ObjectMapper().readTree(response);
        assertEquals(409, problem.path("status").asInt());
        assertTrue(problem.path("retryable").asBoolean());
        String detail = problem.path("detail").asText();
        assertFalse(detail.isBlank());
        assertFalse(detail.contains("Wallet"));
        assertFalse(detail.contains("#"));
        assertEquals(3, ConflictEveryAttemptConfig.injectedConflicts());
    }

    @Test
    void repeatedPostWithTheSameKeyMovesMoneyOnce() throws Exception {
        String senderEmail = "idempotent-sender@example.com";
        Wallet sender = fundedWallet(senderEmail, "100.00");
        Wallet receiver = fundedWallet("idempotent-receiver@example.com", "0.00");

        String body = """
                {
                  "receiverWalletId": %d,
                  "amount": 25.00
                }
                """.formatted(receiver.getId());

        HttpResponse<String> first = post(senderEmail, body, "retry-1");
        HttpResponse<String> replay = post(senderEmail, body, "retry-1");

        assertEquals(200, first.statusCode());
        assertEquals(200, replay.statusCode());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode firstResponse = mapper.readTree(first.body());
        JsonNode replayResponse = mapper.readTree(replay.body());
        String firstId = firstResponse.path("id").asText();
        String replayId = replayResponse.path("id").asText();

        assertEquals(firstId, replayId);
        assertEquals(firstResponse, replayResponse);
        assertEquals(1L, transferCount());
        assertEquals(
                0,
                walletBalance(receiver.getId())
                        .compareTo(new BigDecimal("25.00")));
    }

    @Test
    void sameKeyWithADifferentAmountIs422AndMovesNoMoney() throws Exception {
        String senderEmail = "reused-key-sender@example.com";
        Wallet sender = fundedWallet(senderEmail, "100.00");
        Wallet receiver = fundedWallet("reused-key-receiver@example.com", "0.00");

        String firstBody = """
                {
                  "receiverWalletId": %d,
                  "amount": 25.00
                }
                """.formatted(receiver.getId());

        String changedBody = """
                {
                  "receiverWalletId": %d,
                  "amount": 500.00
                }
                """.formatted(receiver.getId());

        HttpResponse<String> first = post(senderEmail, firstBody, "reuse-1");
        HttpResponse<String> reused = post(senderEmail, changedBody, "reuse-1");

        assertEquals(200, first.statusCode());
        assertEquals(422, reused.statusCode());

        JsonNode problem = new ObjectMapper().readTree(reused.body());
        assertEquals(422, problem.path("status").asInt());
        assertTrue(
                problem.has("retryable"),
                () -> "no retryable field in body: " + reused.body());
        assertFalse(problem.path("retryable").asBoolean());

        assertEquals(1L, transferCount());
        assertEquals(
                0,
                walletBalance(receiver.getId())
                        .compareTo(new BigDecimal("25.00")));
    }

    @Test
    void aKeyClaimedButNotSettledIs409Retryable() throws Exception {
        String senderEmail = "in-progress-sender@example.com";
        Wallet sender = fundedWallet(senderEmail, "100.00");
        Wallet receiver = fundedWallet("in-progress-receiver@example.com", "0.00");

        BigDecimal amount = new BigDecimal("25.00");
        String key = "in-progress-key";
        String scope = "transfer:" + sender.getId();

        String fingerprint = idempotencyService.fingerprint(
                sender.getId(),
                receiver.getId(),
                amount);

        assertTrue(idempotencyRepository.claim(new IdempotencyRecord(
                scope,
                key,
                IdempotencyStatus.IN_PROGRESS,
                null,
                fingerprint,
                Instant.now())).isEmpty());

        String body = """
                {
                  "receiverWalletId": %d,
                  "amount": 25.00
                }
                """.formatted(receiver.getId());

        HttpResponse<String> blocked = post(senderEmail, body, key);

        assertEquals(409, blocked.statusCode());

        JsonNode problem = new ObjectMapper().readTree(blocked.body());
        assertTrue(problem.path("retryable").asBoolean());

        assertEquals(1, idempotencyRepository.release(scope, key));

        HttpResponse<String> retried = post(senderEmail, body, key);

        assertEquals(200, retried.statusCode());
        assertEquals(1L, transferCount());
    }

    @Test
    void twoDifferentKeysFromOneSenderBothTransfer() throws Exception {
        String senderEmail = "two-keys-sender@example.com";
        Wallet sender = fundedWallet(senderEmail, "100.00");
        Wallet receiver = fundedWallet("two-keys-receiver@example.com", "0.00");

        String body = """
                {
                  "receiverWalletId": %d,
                  "amount": 10.00
                }
                """.formatted(receiver.getId());

        HttpResponse<String> first = post(senderEmail, body, "first-key");
        HttpResponse<String> second = post(senderEmail, body, "second-key");

        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());

        ObjectMapper mapper = new ObjectMapper();
        String firstId = mapper.readTree(first.body()).path("id").asText();
        String secondId = mapper.readTree(second.body()).path("id").asText();

        assertNotEquals(firstId, secondId);
        assertEquals(2L, transferCount());
        assertEquals(
                0,
                walletBalance(receiver.getId())
                        .compareTo(new BigDecimal("20.00")));
    }

    @Test
    void postWithoutTheHeaderStillTransfers() throws Exception {
        String senderEmail = "without-header-sender@example.com";
        Wallet sender = fundedWallet(senderEmail, "100.00");
        Wallet receiver = fundedWallet("without-header-receiver@example.com", "0.00");

        String body = """
                {
                  "receiverWalletId": %d,
                  "amount": 25.00
                }
                """.formatted(receiver.getId());

        HttpResponse<String> result = post(senderEmail, body, null);

        assertEquals(200, result.statusCode());
        assertEquals(1L, transferCount());
        assertEquals(0L, idempotencyKeyCount());
    }

    @Test
    void theSameKeyFromTwoDifferentSendersBothTransfer() throws Exception {
        String firstSenderEmail = "shared-key-first@example.com";
        String secondSenderEmail = "shared-key-second@example.com";
        fundedWallet(firstSenderEmail, "100.00");
        fundedWallet(secondSenderEmail, "100.00");
        Wallet receiver = fundedWallet("shared-key-receiver@example.com", "0.00");

        String body = """
                {
                  "receiverWalletId": %d,
                  "amount": 10.00
                }
                """.formatted(receiver.getId());

        HttpResponse<String> first = post(firstSenderEmail, body, "shared-key");
        HttpResponse<String> second = post(secondSenderEmail, body, "shared-key");

        assertEquals(200, first.statusCode());
        assertEquals(200, second.statusCode());

        ObjectMapper mapper = new ObjectMapper();
        assertNotEquals(
                mapper.readTree(first.body()).path("id").asText(),
                mapper.readTree(second.body()).path("id").asText());
        assertEquals(2L, transferCount());
        assertEquals(2L, idempotencyKeyCount());
    }

    @Test
    void aKeyIsReusableAfterTheTransferFails() throws Exception {
        String senderEmail = "failed-transfer-sender@example.com";
        Wallet sender = fundedWallet(senderEmail, "10.00");
        Wallet receiver = fundedWallet("failed-transfer-receiver@example.com", "0.00");

        String body = """
                {
                  "receiverWalletId": %d,
                  "amount": 50.00
                }
                """.formatted(receiver.getId());

        HttpResponse<String> failed = post(senderEmail, body, "poor-1");

        assertEquals(409, failed.statusCode());
        JsonNode problem = new ObjectMapper().readTree(failed.body());
        assertEquals(409, problem.path("status").asInt());
        assertEquals(0L, idempotencyKeyCount());

        jdbc.sql("UPDATE wallets SET balance = ? WHERE id = ?")
                .param(new BigDecimal("100.00"))
                .param(sender.getId())
                .update();

        HttpResponse<String> retried = post(senderEmail, body, "poor-1");

        assertEquals(200, retried.statusCode());
        assertEquals(1L, transferCount());
    }

    @TestConfiguration
    static class ConflictEveryAttemptConfig {

        private static volatile Long receiverId;
        private static final AtomicInteger receiverReads = new AtomicInteger();
        private static final AtomicInteger conflictsInjected = new AtomicInteger();

        static void armConflictOn(Long id) {
            receiverId = id;
            receiverReads.set(0);
            conflictsInjected.set(0);
        }

        static void disarm() {
            receiverId = null;
        }

        static int injectedConflicts() {
            return conflictsInjected.get();
        }

        @Bean
        @Primary
        WalletRepository conflictEveryAttemptWalletRepository(
                JpaWalletRepository real,
                JdbcClient jdbc) {
            return new WalletRepository() {
                @Override
                public Optional<Wallet> findByUserId(Long userId) {
                    return real.findByUserId(userId);
                }

                @Override
                public Wallet save(Wallet wallet) {
                    return real.save(wallet);
                }

                @Override
                public boolean existsByUserId(Long userId) {
                    return real.existsByUserId(userId);
                }

                @Override
                public int debitIfSufficient(Long walletId, BigDecimal amount) {
                    return real.debitIfSufficient(walletId, amount);
                }

                @Override
                public Optional<Wallet> findById(Long id) {
                    Optional<Wallet> wallet = real.findById(id);
                    if (id.equals(receiverId)
                            && receiverReads.incrementAndGet() % 2 == 0) {
                        jdbc.sql("UPDATE wallets SET version = version + 1 WHERE id = ?")
                                .param(id)
                                .update();
                        conflictsInjected.incrementAndGet();
                    }
                    return wallet;
                }

                @Override
                public Optional<Wallet> findByIdForUpdate(Long id) {
                    Optional<Wallet> wallet = real.findByIdForUpdate(id);
                    if (id.equals(receiverId)) {
                        receiverReads.incrementAndGet();
                    }
                    return wallet;
                }
            };
        }
    }
}
