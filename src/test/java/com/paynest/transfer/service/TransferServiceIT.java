package com.paynest.transfer.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigDecimal;

import com.paynest.user.repository.UserRepository;
import com.paynest.user.model.User;

import com.paynest.wallet.repository.SpringDataWalletRepository;
import com.paynest.wallet.model.Wallet;

import com.paynest.transfer.exception.SelfTransferException;
import com.paynest.transfer.exception.InsufficientFundsException;
import com.paynest.transfer.dto.TransferResponse;
import com.paynest.transfer.model.TransferDirection;
import com.paynest.transfer.dto.TransferFilter;
import com.paynest.transfer.model.Transfer;

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

    // ============================================================
    // Day-09 step 4(v) — SCAFFOLD. Comments only; you write the code.
    //
    // ONE test. Name it something like:
    //     directionIsSentForTheSenderAndReceivedForTheSameRow
    //
    // WHY THIS BELONGS IN THE IT AND NOT THE UNIT SUITE
    // 4(ii) compares viewerWalletId against transfer.getSenderWalletId()
    // with Objects.equals. Step 3 proved — by mutation, not by argument —
    // that this exact comparison is INVISIBLE to the unit suite: the
    // in-memory fake reflects one Long instance into the wallet and hands
    // the same Wallet back, so `==` compares a reference to itself and all
    // 12 tests stay green. Against Postgres the transfer row and the wallet
    // row arrive from separate queries, so you get two distinct Longs with
    // equal values. Only here can `==` fail.
    //
    // WHAT TO BUILD
    //   1. two funded wallets, via fundedWallet(...) — read its KEEP
    //      comment above, it creates the User too, which getByEmail needs.
    //      Use fresh emails, e.g. direction-sender@ / direction-receiver@.
    //   2. assertTrue(sender.getId() > 127L, "...outside the Long cache")
    //      and the same for the receiver. The test DB only DELETEs rows and
    //      never resets the sequence, so large ids are LIKELY but not
    //      guaranteed — assert the precondition rather than hoping for it.
    //      (Same reasoning as the step-3 IT right above.)
    //   3. one transfers.transfer(sender, receiver, 25.00).
    //
    // THE ASSERTION THAT MATTERS: ONE ROW, BOTH ANSWERS.
    // Build the response twice from the SAME Transfer, changing only the
    // viewer:
    //
    //     var asSender   = TransferResponse.from(transfer, sender.getId());
    //     var asReceiver = TransferResponse.from(transfer, receiver.getId());
    //
    //     assertEquals(TransferDirection.SENT,     asSender.direction());
    //     assertEquals(TransferDirection.RECEIVED, asReceiver.direction());
    //
    // Assert BOTH. A test that only checks SENT passes against a `from`
    // that returns SENT unconditionally — and "always SENT" is the most
    // likely way to get this wrong, because it is what you see while
    // developing create(). Both halves, on one row, or the test proves
    // nothing about per-viewer behaviour.
    //
    // You will need imports: TransferResponse (dto) and TransferDirection
    // (model). Neither is imported in this file yet. Add them yourself —
    // I am not planting imports for code you have not written.
    //
    // ALSO WORTH ASSERTING, same test or a second one: asSender.id() equals
    // asReceiver.id(). It is the sentence "this is one row seen twice"
    // written as an assertion, and it is what stops a future refactor from
    // quietly building direction by cloning the transfer per viewer.
    //
    // THEN MUTATE, AND PREDICT BEFORE YOU RUN (write your predictions down
    // first — step 3 is the cautionary tale: I predicted the == mutation
    // would turn a unit test red, it stayed 12/12 green, and I guessed wrong
    // twice more before reading the collaborator):
    //
    //   (a) Objects.equals -> == in 4(ii)'s comparison.
    //       Expect: THIS test red on the SENT assertion — the viewer's Long
    //       and the row's Long are different objects, so the sender is
    //       misread as "not the sender" and renders RECEIVED. Expect the
    //       whole unit suite to stay green. That contrast is the finding.
    //
    //   (b) invert the branch (sender -> RECEIVED, else SENT).
    //       Expect: BOTH assertions red, in one test. If only one goes red,
    //       your two viewers are not actually different — check step 2.
    //
    //   (c) make `from` ignore the viewer and always return SENT.
    //       Expect: only the RECEIVED assertion red. This is the mutation
    //       that justifies asserting both halves; if it stays green, the
    //       test is decorative.
    //
    // Restore each one and verify by reading the line back, not by assuming.
    // Expected suite total once 4(iv) and 4(v) are in: 97/97
    // (TransferServiceTest 12 + 4 = 16, TransferServiceIT 5 + 1 = 6,
    //  everything else unchanged at 74) — but TRUST THE RUN, NOT MY
    //  ARITHMETIC. If the number differs, find out why before moving on;
    //  a surprise count has caught a silently-skipped suite before.
    // ============================================================

    @Test
    void directionIsSentForTheSenderAndReceivedForTheSameRow() {
        Wallet sender = fundedWallet(
                "direction-sender@example.com",
                "100.00");

        Wallet receiver = fundedWallet(
                "direction-receiver@example.com",
                "10.00");

        assertTrue(
                sender.getId() > 127L,
                "sender wallet id must be outside the Long cache");

        assertTrue(
                receiver.getId() > 127L,
                "receiver wallet id must be outside the Long cache");

        var transfer = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        TransferResponse asSender = TransferResponse.from(
                transfer,
                sender.getId());

        TransferResponse asReceiver = TransferResponse.from(
                transfer,
                receiver.getId());

        assertEquals(asSender.id(), asReceiver.id());
        assertEquals(TransferDirection.SENT, asSender.direction());
        assertEquals(TransferDirection.RECEIVED, asReceiver.direction());
    }

    // =======================================================================
    //  DAY-09, STEP 5(vi) — THE IT. THE ONLY TEST THAT CAN FIND STEP 5'S BUG.
    //
    //  Everything in 5(v) runs against chained .filter() calls. ONLY THIS
    //  FILE executes the JPQL, so only this file can catch:
    //      * the AND/OR precedence bug (the big one)
    //      * a from/to or min/max swap at the unpack site
    //      * a BigDecimal scale mismatch (25.00 vs NUMERIC(19,4) 25.0000)
    //      * a `<` where you documented `<=`
    //
    //  Write TWO tests. They are not interchangeable.
    //
    //  ----------------------------------------------------------------------
    //  5(vi)(1) THE PARENTHESIS TEST — write this one FIRST and deliberately.
    //
    //  THE SHAPE THAT CATCHES IT: the viewer must have a transfer they SENT
    //  that the filter EXCLUDES.
    //
    //      wallet A (the viewer), wallets B and C
    //      transfer 1:  A -> B   amount 10.00     <- SENT, must be EXCLUDED
    //      transfer 2:  A -> C   amount 90.00     <- SENT, must be INCLUDED
    //      transfer 3:  B -> A   amount 95.00     <- RECEIVED, INCLUDED
    //
    //      filter: minAmount = 50.00, everything else null
    //      expect: exactly TWO rows (2 and 3); transfer 1 ABSENT.
    //
    //  WHY THIS EXACT SHAPE. With the parentheses wrong the query becomes
    //  `sender = :w OR (receiver = :w AND amount >= :min)`, so EVERY sent
    //  transfer comes back regardless of amount — and transfer 1 appears.
    //  A test built only from RECEIVED rows passes under the bug. A test
    //  whose sent rows all happen to match the filter passes under the bug.
    //  The excluded-sent row is the whole test.
    //
    //  ⚠️ ASSERT THE ABSENCE SPECIFICALLY, by id: assert the page does not
    //  contain transfer 1's id. `assertEquals(2, page.getTotalElements())`
    //  alone is weaker than it looks — under the bug you would get 3, which
    //  it does catch, but if a later edit changes the row count for an
    //  unrelated reason the test fails without telling you why.
    //
    //  ⚠️ AND ASSERT NON-EMPTY FIRST. Both the swap failure modes and a
    //  broken bind produce an empty page, which satisfies "transfer 1 is
    //  absent" perfectly.
    //  ----------------------------------------------------------------------
    @Test
    void amountFilterExcludesASentTransferAsWellAsIncludingMatchingRows() {
        Wallet viewer = fundedWallet("filter-a@example.com", "100.00");
        Wallet firstReceiver = fundedWallet("filter-b@example.com", "100.00");
        Wallet secondReceiver = fundedWallet("filter-c@example.com", "10.00");

        Transfer excludedSentTransfer = transfers.transfer(
                viewer.getId(),
                firstReceiver.getId(),
                new BigDecimal("10.00"));

        Transfer includedSentTransfer = transfers.transfer(
                viewer.getId(),
                secondReceiver.getId(),
                new BigDecimal("90.00"));

        Transfer includedReceivedTransfer = transfers.transfer(
                firstReceiver.getId(),
                viewer.getId(),
                new BigDecimal("95.00"));

        TransferFilter filter = new TransferFilter(
                null,
                null,
                null,
                new BigDecimal("50.00"),
                null);

        var page = transfers.history(
                viewer.getId(),
                "filter-a@example.com",
                filter,
                PageRequest.of(0, 20));

        assertFalse(page.isEmpty());
        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(
                transfer -> transfer.getId().equals(
                        includedSentTransfer.getId())));
        assertTrue(page.getContent().stream().anyMatch(
                transfer -> transfer.getId().equals(
                        includedReceivedTransfer.getId())));
        assertTrue(page.getContent().stream().noneMatch(
                transfer -> transfer.getId().equals(
                        excludedSentTransfer.getId())));
    }
    //  ----------------------------------------------------------------------
    //  5(vi)(2) THE BOUNDARY + SCALE TEST.
    //
    //      amounts 25.00 and 50.00; filter minAmount = 25.00
    //      expect: BOTH rows if you documented inclusive (>=)
    //
    //  ⚠️ THIS IS THE SCALE TEST TOO, and that is why it must be an IT.
    //  You write "25.00"; Postgres stores and returns 25.0000. If anything
    //  in the chain compares with equals rather than compareTo, the row on
    //  the boundary vanishes HERE and nowhere else. Assert the returned
    //  amount with compareTo, exactly as WalletRepositoryIT does for
    //  wallets.balance.
    //
    //  ⚠️ IF YOU TEST THE DATE RANGE HERE: createdAt comes from the database
    //  DEFAULT now(), so all rows land within milliseconds of each other and
    //  you cannot construct a meaningful boundary by inserting normally. Read
    //  an actual createdAt back off a saved transfer and build the filter
    //  FROM that value — then `from = thatInstant` tests `>=` against a row
    //  sitting exactly on it. Do not use Instant.now() in the test and hope
    //  it straddles the rows; that is a clock race, and it will pass locally
    //  and fail in CI.
    //  ----------------------------------------------------------------------
    @Test
    void amountFilterIncludesTheMinimumBoundaryWithDatabaseScale() {
        Wallet sender = fundedWallet("filter-scale-sender@example.com", "100.00");
        Wallet receiver = fundedWallet("filter-scale-receiver@example.com", "10.00");

        Transfer lowerBoundary = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        Transfer aboveBoundary = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("50.00"));

        TransferFilter filter = new TransferFilter(
                null,
                null,
                null,
                new BigDecimal("25.00"),
                null);

        var page = transfers.history(
                sender.getId(),
                "filter-scale-sender@example.com",
                filter,
                PageRequest.of(0, 20));

        assertFalse(page.isEmpty());
        assertEquals(2, page.getTotalElements());

        Transfer returnedLowerBoundary = page.getContent().stream()
                .filter(transfer -> transfer.getId().equals(
                        lowerBoundary.getId()))
                .findFirst()
                .orElseThrow();

        Transfer returnedAboveBoundary = page.getContent().stream()
                .filter(transfer -> transfer.getId().equals(
                        aboveBoundary.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(
                0,
                returnedLowerBoundary.getAmount().compareTo(
                        new BigDecimal("25.00")));

        assertEquals(
                0,
                returnedAboveBoundary.getAmount().compareTo(
                        new BigDecimal("50.00")));
    }
    //  ----------------------------------------------------------------------
    //  ⚠️ THIS CLASS COMMITS — no @Transactional. deleteAllRows() runs in
    //  @BeforeEach AND @AfterEach and your new rows must be covered by it.
    //  Three wallets and three transfers left behind will FK-break suites you
    //  never touched [[a-committing-test-suite-must-clean-up-after-itself]].
    //  Use fundedWallet() and unique emails (`filter-a@`, `filter-b@`,
    //  `filter-c@`).
    //
    //  ⚠️ CHECK THE DATASOURCE BEFORE RUNNING. The jpa-it profile must point
    //  at paynest_test [[it-profile-must-target-test-db]].
    //
    //  ⚠️ AND THE MUTATION THAT CLOSES STEP 5 — do this one, it is the point:
    //  remove the parentheses around the sender-OR-receiver clause in the
    //  @Query, so it reads
    //      WHERE t.senderWalletId = :walletId
    //         OR t.receiverWalletId = :walletId AND (...filters...)
    //  PREDICT FIRST, IN WRITING: which test, which assertion, what numbers.
    //  My prediction: 5(vi)(1) red on the "transfer 1 is absent" assertion
    //  (3 rows where 2 expected), and **every test in 5(v) still GREEN** —
    //  because the fake has no SQL. If the unit tests go red too, my model of
    //  the fake is wrong and that is the finding.
    //  Then restore it, and verify the restore by reading the query back.
    // =======================================================================

    // =======================================================================
    //  DAY-09, STEP 5(vii) — THE DATE FILTER. The untested half of step 5.
    //
    //  ⚠️ WHY THIS IS A SEPARATE SUB-STEP AND NOT AN OPTIONAL EXTRA. I
    //  checked what the suite currently executes:
    //
    //      TransferServiceTest : emptyFilter / statusFilter / amountRange
    //      TransferServiceIT   : amountFilter x2
    //
    //  `grep -n Instant` over both test files finds **no test at all** that
    //  sets `from` or `to`. So two of the five filter fields, and two of the
    //  four CAST clauses you just added, have **never run**. The 101-green
    //  suite is silent about them, and that silence is indistinguishable from
    //  approval [[verify-steps-by-running-not-by-green-tests]].
    //
    //  Concretely, right now nothing in the build would catch:
    //    * `>=` written as `>` on `from` (your javadoc promises inclusive)
    //    * `<` written as `<=` on `to` (your javadoc promises half-open)
    //    * from/to swapped at the unpack site in JpaTransferRepository
    //    * `CAST(:from AS timestamp)` naming the wrong Hibernate type
    //
    //  ----------------------------------------------------------------------
    //  5(vii)(1) THE `from` BOUNDARY IS INCLUSIVE.
    //
    //  ⚠️ THE HARD PART IS CONSTRUCTING THE DATA, NOT THE ASSERTION.
    //  `createdAt` is `insertable = false` with `DEFAULT now()`, so you
    //  cannot choose it. Three transfers created in a row land within
    //  milliseconds of each other, so `Instant.now()` in the test does not
    //  reliably fall between them — that is a clock race that passes locally
    //  and fails in CI.
    //
    //  DO THIS INSTEAD: create the transfers, then READ a createdAt back off
    //  a saved row and build the filter FROM that exact value:
    //
    //      Transfer second = ... ;              // the middle row
    //      Instant boundary = second.getCreatedAt();
    //      filter = from = boundary, everything else null
    //
    //  Now `from = boundary` tests `>=` against a row sitting EXACTLY on it.
    //  Expect the middle row PRESENT (that is what inclusive means) plus any
    //  row created after it. If you wrote `>`, the middle row vanishes and
    //  this is the only test that would ever say so.
    //
    //  ⚠️ ORDERING CAVEAT: `now()` in Postgres is the TRANSACTION start time,
    //  not statement time. Each `transfers.transfer(...)` here is its own
    //  transaction, so the three timestamps do differ — but if you ever batch
    //  them into one transaction they would be IDENTICAL and this test would
    //  become meaningless while still passing. Assert the three createdAt
    //  values are distinct before relying on them; a test built on data that
    //  silently collapsed is the empty-page trap in a new shape
    //  [[a-passing-assertion-on-empty-data-proves-nothing]].
    //  ----------------------------------------------------------------------
    @Test
    void fromDateFilterIncludesTheTransferAtItsBoundary() {
        Wallet sender = fundedWallet(
                "date-filter-1-sender@example.com",
                "100.00");

        Wallet receiver = fundedWallet(
                "date-filter-1-receiver@example.com",
                "10.00");

        Transfer first = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("10.00"));

        Transfer boundaryTransfer = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("20.00"));

        Transfer laterTransfer = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"));

        assertNotEquals(first.getCreatedAt(), boundaryTransfer.getCreatedAt());
        assertNotEquals(boundaryTransfer.getCreatedAt(), laterTransfer.getCreatedAt());

        assertTrue(first.getCreatedAt().isBefore(
                boundaryTransfer.getCreatedAt()));
        assertTrue(boundaryTransfer.getCreatedAt().isBefore(
                laterTransfer.getCreatedAt()));

        TransferFilter filter = new TransferFilter(
                null,
                boundaryTransfer.getCreatedAt(),
                null,
                null,
                null);

        var page = transfers.history(
                sender.getId(),
                "date-filter-1-sender@example.com",
                filter,
                PageRequest.of(0, 20));

        assertFalse(page.isEmpty());
        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(
                transfer -> transfer.getId().equals(
                        boundaryTransfer.getId())));
        assertTrue(page.getContent().stream().anyMatch(
                transfer -> transfer.getId().equals(
                        laterTransfer.getId())));
        assertTrue(page.getContent().stream().noneMatch(
                transfer -> transfer.getId().equals(first.getId())));
    }
    //  ----------------------------------------------------------------------
    //  5(vii)(2) THE `to` BOUNDARY IS EXCLUSIVE.
    //
    //  Same construction, opposite assertion: with `to = boundary`, the row
    //  AT the boundary must be ABSENT, because your javadoc commits to
    //  [from, to). Expect only rows strictly older than it.
    //
    //  ⚠️ WRITE THIS ONE EVEN THOUGH IT FEELS LIKE A MIRROR OF (1). The two
    //  bounds use DIFFERENT operators in your query (`>=` and `<`), so they
    //  are two independent facts. A single test covering both bounds cannot
    //  distinguish "both correct" from "both inclusive" — and both-inclusive
    //  is the single most likely way to write this.
    //  ----------------------------------------------------------------------
    @Test
    void toDateFilterExcludesTheTransferAtItsBoundary() {
        Wallet sender = fundedWallet(
                "date-filter-2-sender@example.com",
                "100.00");

        Wallet receiver = fundedWallet(
                "date-filter-2-receiver@example.com",
                "10.00");

        Transfer first = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("10.00"));

        Transfer boundaryTransfer = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("20.00"));

        Transfer laterTransfer = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"));

        assertNotEquals(first.getCreatedAt(), boundaryTransfer.getCreatedAt());
        assertNotEquals(boundaryTransfer.getCreatedAt(), laterTransfer.getCreatedAt());

        assertTrue(first.getCreatedAt().isBefore(
                boundaryTransfer.getCreatedAt()));
        assertTrue(boundaryTransfer.getCreatedAt().isBefore(
                laterTransfer.getCreatedAt()));

        TransferFilter filter = new TransferFilter(
                null,
                null,
                boundaryTransfer.getCreatedAt(),
                null,
                null);

        var page = transfers.history(
                sender.getId(),
                "date-filter-2-sender@example.com",
                filter,
                PageRequest.of(0, 20));

        assertFalse(page.isEmpty());
        assertEquals(1, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(
                transfer -> transfer.getId().equals(first.getId())));
        assertTrue(page.getContent().stream().noneMatch(
                transfer -> transfer.getId().equals(
                        boundaryTransfer.getId())));
        assertTrue(page.getContent().stream().noneMatch(
                transfer -> transfer.getId().equals(laterTransfer.getId())));
    }
    //  ----------------------------------------------------------------------
    //  5(vii)(3) BOTH BOUNDS TOGETHER, AND THE SWAP.
    //
    //  Set `from` AND `to` to a window containing exactly one of three rows.
    //  This is the only shape that can catch a from/to swap at the unpack
    //  site: with one bound set a swap is invisible, because the other
    //  parameter is null and its clause disappears.
    //
    //  ⚠️ ASSERT NON-EMPTY FIRST, then assert WHICH row. A swap returns an
    //  empty page — `created_at >= to AND created_at < from` is unsatisfiable
    //  — and "the excluded rows are absent" passes perfectly when everything
    //  is absent.
    //  ----------------------------------------------------------------------
    @Test
    void dateRangeWithBothBoundsReturnsOnlyTheTransferInsideTheWindow() {
        Wallet sender = fundedWallet(
                "date-filter-3-sender@example.com",
                "100.00");

        Wallet receiver = fundedWallet(
                "date-filter-3-receiver@example.com",
                "10.00");

        Transfer first = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("10.00"));

        Transfer insideWindow = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("20.00"));

        Transfer upperBoundary = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"));

        assertNotEquals(first.getCreatedAt(), insideWindow.getCreatedAt());
        assertNotEquals(insideWindow.getCreatedAt(), upperBoundary.getCreatedAt());

        assertTrue(first.getCreatedAt().isBefore(insideWindow.getCreatedAt()));
        assertTrue(insideWindow.getCreatedAt().isBefore(
                upperBoundary.getCreatedAt()));

        TransferFilter filter = new TransferFilter(
                null,
                insideWindow.getCreatedAt(),
                upperBoundary.getCreatedAt(),
                null,
                null);

        var page = transfers.history(
                sender.getId(),
                "date-filter-3-sender@example.com",
                filter,
                PageRequest.of(0, 20));

        assertFalse(page.isEmpty());
        assertEquals(1, page.getTotalElements());
        assertEquals(insideWindow.getId(), page.getContent().getFirst().getId());
        assertTrue(page.getContent().stream().noneMatch(
                transfer -> transfer.getId().equals(first.getId())));
        assertTrue(page.getContent().stream().noneMatch(
                transfer -> transfer.getId().equals(upperBoundary.getId())));
    }
    //  ----------------------------------------------------------------------
    //  ⚠️ CLEANUP: `deleteAllRows()` runs in @BeforeEach AND @AfterEach and
    //  covers transfers/wallets/users, so new rows are handled — but use
    //  unique emails (`date-filter-1@` ...) or `fundedWallet` throws
    //  DuplicateEmailException from the arrange block
    //  [[a-committing-test-suite-must-clean-up-after-itself]].
    //
    //  ⚠️ THE MUTATION THAT CLOSES 5(vii) — and PREDICT BEFORE RUNNING:
    //  change `t.createdAt >= :from` to `t.createdAt > :from` in the @Query.
    //  Write down which test, which assertion, which row. My prediction:
    //  5(vii)(1) red on its "boundary row is present" assertion; 5(vii)(2)
    //  GREEN (it only constrains `to`); all 3 unit filter tests GREEN (the
    //  fake has no SQL, and none of them sets a date). If 5(vii)(3) also goes
    //  red, say so — it means my model of which clause each test exercises is
    //  wrong, and that is the finding.
    //  Then restore, and verify the restore by reading the line back.
    // =======================================================================

    // =======================================================================
    //  DAY-09, STEP 5(ix) — the 4-decimal-place experiment.
    //
    //  ⚠️ MOVED HERE BY ME, 9 Nov 2026. You wrote this test into
    //  SpringDataTransferRepository.java — the repository INTERFACE, in
    //  src/main/java. That is what broke the build:
    //
    //      SpringDataTransferRepository.java:[117,6] cannot find symbol
    //        symbol: class Test
    //
    //  ⚠️ AND MY SCAFFOLD INVITED THAT. I put the 5(ix) decision box in the
    //  repository file (correct — the CAST lives there) and then asked for a
    //  test inside it (wrong — the test belongs where tests compile). A
    //  scaffold must say WHICH FILE its code goes in when that differs from
    //  the file the comment sits in. Mine did not.
    //
    //  Your test body is unchanged; only its home moved.
    // =======================================================================
    @Test
    void amountFilterKeepsFourDecimalPlacesInItsMinimumBound() {
        Wallet sender = fundedWallet(
                "filter-scale-4dp-sender@example.com",
                "100.00");

        Wallet receiver = fundedWallet(
                "filter-scale-4dp-receiver@example.com",
                "10.00");

        Transfer belowMinimum = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.0001"));

        Transfer atMinimum = transfers.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.0002"));

        TransferFilter filter = new TransferFilter(
                null,
                null,
                null,
                new BigDecimal("25.0002"),
                null);

        var page = transfers.history(
                sender.getId(),
                "filter-scale-4dp-sender@example.com",
                filter,
                PageRequest.of(0, 20));

        assertFalse(page.isEmpty());
        assertEquals(1, page.getTotalElements());
        assertEquals(atMinimum.getId(), page.getContent().getFirst().getId());
        assertTrue(page.getContent().stream().noneMatch(
                transfer -> transfer.getId().equals(belowMinimum.getId())));
    }

}
