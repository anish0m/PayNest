package com.paynest.transfer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.paynest.config.JwtService;

import com.paynest.user.model.User;
import com.paynest.user.repository.UserRepository;

import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.JpaWalletRepository;
import com.paynest.wallet.repository.SpringDataWalletRepository;
import com.paynest.wallet.repository.WalletRepository;

import java.math.BigDecimal;

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
