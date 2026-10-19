package com.paynest.transfer.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import com.paynest.user.repository.UserRepository;
import com.paynest.user.model.User;

import com.paynest.wallet.repository.SpringDataWalletRepository;
import com.paynest.wallet.model.Wallet;

import com.paynest.transfer.exception.SelfTransferException;
import com.paynest.transfer.exception.InsufficientFundsException;

// No @Transactional: the service's REQUIRED would join the test's transaction, leaving nothing to roll back.
@SpringBootTest
@ActiveProfiles({"jpa", "jpa-it"})
class TransferServiceIT {

    @Autowired
    private TransferService transfers;

    @Autowired
    private SpringDataWalletRepository wallets;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    private void deleteAllRows() {
        jdbc.sql("DELETE FROM transfers").update();
        jdbc.sql("DELETE FROM wallets").update();
        jdbc.sql("DELETE FROM users").update();
    }

    @BeforeEach
    void clean() {
        deleteAllRows();
    }

    // KEEP: this suite commits its data, so cleanup must protect later suites.
    @AfterEach
    void cleanAfterTest() {
        deleteAllRows();
    }

    // KEEP: the raw UPDATE funds the database row behind Hibernate's back, so
    // this returned Wallet still has balance 0. Use it only for its ID; read
    // balances through JdbcClient to avoid asserting against stale state.
    private Wallet fundedWallet(String email, String amount) {
        User user = users.save(
                new User("Transfer", "User", email, "Pass1234#")
        );

        Wallet wallet = wallets.saveAndFlush(new Wallet(user));

        jdbc.sql("UPDATE wallets SET balance = ? WHERE id = ?")
                .param(new BigDecimal(amount))
                .param(wallet.getId())
                .update();

        return wallet;
    }

    @Test
    void completedTransferUpdatesBothBalancesAndCreatesTransfer() {
        Wallet sender = fundedWallet("sender@example.com", "100.00");
        Wallet receiver = fundedWallet("receiver@example.com", "10.00");

        transfers.transfer(sender.getId(), receiver.getId(), new BigDecimal("25.00"));

        BigDecimal senderBalance = jdbc.sql(
                        "SELECT balance FROM wallets WHERE id = ?")
                .param(sender.getId())
                .query(BigDecimal.class)
                .single();

        BigDecimal receiverBalance = jdbc.sql(
                        "SELECT balance FROM wallets WHERE id = ?")
                .param(receiver.getId())
                .query(BigDecimal.class)
                .single();

        Integer transferCount = jdbc.sql(
                        "SELECT COUNT(*) FROM transfers")
                .query(Integer.class)
                .single();

        String status = jdbc.sql(
                        "SELECT status FROM transfers")
                .query(String.class)
                .single();

        assertEquals(0, senderBalance.compareTo(new BigDecimal("75.00")));
        assertEquals(0, receiverBalance.compareTo(new BigDecimal("35.00")));
        assertEquals(1, transferCount);
        assertEquals("COMPLETED", status);

        Long recordedSenderId = jdbc.sql(
                        "SELECT sender_wallet_id FROM transfers")
                .query(Long.class)
                .single();

        Long recordedReceiverId = jdbc.sql(
                        "SELECT receiver_wallet_id FROM transfers")
                .query(Long.class)
                .single();

        // KEEP: assert sender and receiver IDs separately with different
        // balances. The service takes amount last, but Transfer stores amount
        // first; swapping the adjacent wallet IDs still compiles and moves
        // money, while naming the wrong parties in API responses.
        assertEquals(sender.getId(), recordedSenderId);
        assertEquals(receiver.getId(), recordedReceiverId);
    }

    // The receiver balance is written directly to PostgreSQL by
    // fundedWallet(), while the returned entity still holds 0. The
    // managedReceiver re-read sees the numeric(19,4) ceiling and throws
    // SQLState 22003 after the debit. This depends on step 5's
    // clearAutomatically; removing it would invalidate this rollback test.
    @Test
    void failedTransferRollsBackTheDebit() {
        Wallet sender = fundedWallet("rollback-sender@example.com", "100.00");

        Wallet receiver = fundedWallet(
                "rollback-receiver@example.com",
                "999999999999999.9999"
        );

        // RuntimeException already triggers Spring's default rollback, so this
        // test does not specifically prove rollbackFor = Exception.class.
        assertThrows(
                DataIntegrityViolationException.class,
                () -> transfers.transfer(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("25.00"))
        );

        BigDecimal senderBalance = jdbc.sql(
                        "SELECT balance FROM wallets WHERE id = ?")
                .param(sender.getId())
                .query(BigDecimal.class)
                .single();

        Integer transferCount = jdbc.sql(
                        "SELECT COUNT(*) FROM transfers")
                .query(Integer.class)
                .single();

        assertEquals(0, senderBalance.compareTo(new BigDecimal("100.00")));
        assertEquals(0, transferCount);

    }

    @Test
    void insufficientFundsLeavesBalanceAndTransfersUnchanged() {
        Wallet sender = fundedWallet("insufficient-sender@example.com", "100.00");
        Wallet receiver = fundedWallet("insufficient-receiver@example.com", "10.00");

        assertThrows(
                InsufficientFundsException.class,
                () -> transfers.transfer(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("500.00"))
        );

        BigDecimal senderBalance = jdbc.sql(
                        "SELECT balance FROM wallets WHERE id = ?")
                .param(sender.getId())
                .query(BigDecimal.class)
                .single();

        Integer transferCount = jdbc.sql(
                        "SELECT COUNT(*) FROM transfers")
                .query(Integer.class)
                .single();

        assertEquals(0, senderBalance.compareTo(new BigDecimal("100.00")));
        assertEquals(0, transferCount);
    }

    @Test
    void selfTransferIsRejectedWithoutChangingTheBalance() {
        Wallet wallet = fundedWallet("self-transfer@example.com", "100.00");

        assertThrows(
                SelfTransferException.class,
                () -> transfers.transfer(
                        wallet.getId(),
                        wallet.getId(),
                        new BigDecimal("25.00"))
        );

        BigDecimal balance = jdbc.sql(
                        "SELECT balance FROM wallets WHERE id = ?")
                .param(wallet.getId())
                .query(BigDecimal.class)
                .single();

        Integer transferCount = jdbc.sql(
                        "SELECT COUNT(*) FROM transfers")
                .query(Integer.class)
                .single();

        assertEquals(0, balance.compareTo(new BigDecimal("100.00")));
        assertEquals(0, transferCount);
    }
}
