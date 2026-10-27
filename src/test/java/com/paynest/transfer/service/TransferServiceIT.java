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
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    // ============================================================
    // STEP 3(vii) — the test that CAN catch `==` in findById's gate
    // ============================================================
    //
    // WHY THIS LIVES HERE AND NOT IN TransferServiceTest:
    // we ran the mutation. Replacing Objects.equals with == in findById's
    // ownership gate left all 12 unit tests GREEN, and no arrangement of
    // walletWithIdAboveLongCache can change that. The reason is the fake:
    // InMemoryWalletRepository.assignId reflects ONE Long instance into the
    // wallet's id field, and findById hands back that same Wallet from its
    // map. Transfer was built from sender.getId(). So both sides of the
    // comparison are literally the same object — `==` compares a reference
    // to itself, no boxing happens, and the Long cache is never involved.
    // Pushing the id past 127 is irrelevant when nothing is being boxed.
    //
    // (That helper DOES work in the unit test's self-transfer case, but only
    // because that test builds two distinct instances with Long.valueOf(...)
    // by hand — see its KEEP comment. The 200-wallet loop was never the
    // mechanism; the two separate instances were. Worth re-reading.)
    //
    // Against PostgreSQL the shape is different and the mutation becomes
    // detectable: the transfer row and the wallet row are loaded by SEPARATE
    // queries, so Hibernate materialises SEPARATE Long objects for
    // transfer.getSenderWalletId() and callerWallet.getId(). Equal values,
    // different references — exactly where `==` is wrong and `equals` is right.
    //
    // ⚠️ THE IDS MUST EXCEED 127 FOR THIS TO PROVE ANYTHING. Postgres
    // sequences are shared across this test DB and @BeforeEach only DELETEs
    // rows — it never resets the sequence — so ids are usually already large.
    // "Usually" is not a test. ASSERT it, the way the unit helper does:
    //
    //     assertTrue(sender.getId() > 127L, "id must be outside the Long cache");
    //
    // ⚠️ assertTrue IS NOT IMPORTED IN THIS FILE YET — only assertEquals and
    // assertThrows are. Add:
    //     import static org.junit.jupiter.api.Assertions.assertTrue;
    // I did not add it for you, because an unused import sitting in your tree
    // is its own small lie about what the file does.
    //
    // Without that assertion this test could silently stop proving anything if
    // the DB is ever recreated, and it would still pass. A passing assertion
    // on data that cannot fail proves nothing.
    //
    // YOUR TASK:
    //
    //   @Test
    //   void detailsOwnershipComparesIdsByValueNotByReference() {
    //
    @Test
    void detailsOwnershipComparesIdsByValueNotByReference() {
    //
    //   1. Wallet sender   = fundedWallet("details-sender@example.com",   "100.00");
    //      Wallet receiver = fundedWallet("details-receiver@example.com", "10.00");
    //
        Wallet sender = fundedWallet(
                "details-sender@example.com",
                "100.00");

        Wallet receiver = fundedWallet(
                "details-receiver@example.com",
                "10.00");
    //
    //   2. assertTrue on BOTH ids being > 127L (see above)
    //
        assertTrue(
                sender.getId() > 127L,
                "sender wallet id must be outside the Long cache");

        assertTrue(
                receiver.getId() > 127L,
                "receiver wallet id must be outside the Long cache");
    //
    //   3. var transfer = transfers.transfer(
    //              sender.getId(), receiver.getId(), new BigDecimal("25.00"));
    //
        var transfer = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));
    //
    //   4. act as the RECEIVER — this is the side the unit test could not
    //      protect, and it exercises isReceiver:
    //
    //        var found = transfers.findById(
    //                transfer.getId(), "details-receiver@example.com");
    //
        var found = transfers.findById(
                transfer.getId(),
                "details-receiver@example.com");
    //
    //   5. assertEquals(transfer.getId(), found.getId());
    //
        assertEquals(transfer.getId(), found.getId());
    //
    // ⚠️ fundedWallet CREATES THE USER TOO, so getByEmail can resolve the
    // caller: it goes users.getByEmail(email) -> wallets.findByUserId(...).
    // That second query is precisely what produces the distinct Long. Pass the
    // SAME email string you handed fundedWallet, or you get a 404 from
    // WalletNotFoundException(String) and the test fails on the wrong thing.
    //
    // ⚠️ DO NOT ADD @Transactional, and do not remove the @AfterEach. This
    // suite commits; the class comment at the top explains both. Your two new
    // rows are cleaned by the existing deleteAllRows().
    //
    // THEN VERIFY IT BITES — this is the whole point of the test:
    //   mutate findById's gate to
    //       transfer.getReceiverWalletId() == callerWallet.getId()
    //   and run `./mvnw test`. THIS test must go RED with
    //   WalletAccessDeniedException while all 12 unit tests stay GREEN.
    //   That contrast IS the finding: it shows the unit suite is blind here
    //   and the IT is not. Restore the gate and confirm 91/91.
    //
    // ⚠️ If it stays green under the mutation, do not assume the test is
    // wrong — print both ids and their System.identityHashCode first. Same
    // reference means something upstream is sharing the instance, and that is
    // a more interesting finding than the test.
    }
}
