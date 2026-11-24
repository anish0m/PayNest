package com.paynest.transfer.service;

// ===========================================================================
//  DAY-10, STEP 1(i) — PROVE THE LOST UPDATE BEFORE FIXING IT.
//
//  This is the only step of Day-10 that is allowed to fail honestly. Steps 2-6
//  are assembly against an anomaly this file must first demonstrate. If this
//  test passes before any fix exists, the TEST is wrong — not the code — and
//  nothing built on top of it means anything.
//
//  ---------------------------------------------------------------------------
//  WHAT YOU ARE HUNTING, AND WHY 105 GREEN TESTS NEVER SAW IT
//  ---------------------------------------------------------------------------
//
//  Every test in this project so far asks: does ONE request produce the right
//  answer? This one asks a question none of them can express: what happens when
//  TWO requests touch the same row at the same instant.
//
//  Look at TransferService.transfer(). The two halves of the money movement are
//  not protected the same way:
//
//    THE DEBIT   wallets.debitIfSufficient(senderWalletId, amount)
//                → UPDATE wallets SET balance = balance - :amt
//                        WHERE id = :id AND balance >= :amt
//                  One statement. It asks and acts with NO GAP in between, so
//                  there is no window for another transaction to slip into.
//                  `rows == 0` IS the rejection. This half is already safe.
//
//    THE CREDIT  Wallet managedReceiver = wallets.findById(receiverWalletId)...
//                managedReceiver.credit(amount);
//                → SELECT, then +amount in Java, then UPDATE at commit.
//                  READ ... THINK ... WRITE, with a gap between the read and
//                  the write. That gap is the whole defect.
//
//  Two people pay wallet 20 at the same moment:
//
//      A: SELECT balance → 100
//      B: SELECT balance → 100        (A has not committed; READ COMMITTED
//                                      shows B the old value, correctly)
//      A: UPDATE balance = 100 + 30 = 130      commit
//      B: UPDATE balance = 100 + 50 = 150      commit
//
//      Final balance: 150. Expected: 180. The 30 is GONE.
//
//  Nobody did anything wrong. Neither transaction is buggy. B simply computed
//  its new balance from a number that was already stale by the time it wrote.
//  The money vanished in the RELATIONSHIP between two correct requests.
//
//  ⚠️ @Transactional did not fail here. It never promised to help. It buys
//  atomicity and durability — NOT isolation. PostgreSQL's default READ
//  COMMITTED level PERMITS the lost update; this is documented behaviour, not
//  a bug in the database. "Atomic" is not the same as "alone".
//
//  ---------------------------------------------------------------------------
//  WRITE BELOW: the class declaration and its field block.
//  ---------------------------------------------------------------------------
//
//  package + imports: copy the shape of TransferServiceIT in this same folder.
//  You will additionally need, beyond what that file imports:
//
//      org.springframework.transaction.support.TransactionTemplate
//      java.util.concurrent.CountDownLatch
//      java.util.concurrent.ExecutorService
//      java.util.concurrent.Executors
//      java.util.concurrent.TimeUnit
//      java.util.concurrent.atomic.AtomicReference
//
//  ANNOTATIONS — @SpringBootTest and @ActiveProfiles({"jpa", "jpa-it"}),
//  exactly as TransferServiceIT has them.
//
//  🔴 AND **NOT** @Transactional. This is the one decision in the file that
//  cannot be recovered from later, so it gets its own warning:
//
//      A race needs TWO transactions that commit INDEPENDENTLY. Under
//      @Transactional the test method owns a transaction and the service's
//      REQUIRED propagation joins it — so your two "concurrent" transfers
//      become ONE transaction writing twice. A transaction cannot conflict
//      with itself. The test would then pass whether or not the application
//      is safe.
//
//      This is the identical failure shape as a rollback test that never
//      rolled back. TransferServiceIT already carries this warning at its
//      header for the same reason; read it.
//
//  The price of honesty is real cleanup: this suite COMMITS. So you need
//  @BeforeEach and @AfterEach both calling a deleteAllRows() — copy
//  TransferServiceIT's three DELETEs (transfers, wallets, users, in that
//  order; the order is FK-driven, not cosmetic).
//
//  FIELDS — @Autowired: TransferService, SpringDataWalletRepository,
//  UserRepository, JdbcClient. Same four as TransferServiceIT.
//
//  Plus ONE that is new to this project — grep confirms TransactionTemplate
//  appears nowhere in PayNest yet:
//
//      @Autowired
//      private TransactionTemplate tx;
//
//  WHY A TEMPLATE AND NOT AN ANNOTATION. @Transactional is applied by a PROXY
//  at a method boundary. The units of work your two threads run are lambdas,
//  not proxied methods, so an annotation has nothing to attach to and would be
//  silently ignored. TransactionTemplate is the programmatic equivalent: you
//  hand it a lambda, it opens a real transaction, runs it, and COMMITS. That
//  commit, on a thread of its own, is what makes the race real.
//
//  It is an auto-configured bean — Spring Boot provides it wherever a
//  PlatformTransactionManager exists, so no @Bean of your own is needed.
//
//  Reuse TransferServiceIT's fundedWallet(email, amount) helper verbatim,
//  including its KEEP comment. It funds the row with a raw UPDATE behind
//  Hibernate's back, so the returned Wallet's balance field is STALE — use it
//  only for its getId(), and read every balance back through JdbcClient.
// ===========================================================================

import com.paynest.user.model.User;
import com.paynest.user.repository.UserRepository;

import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.SpringDataWalletRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import java.math.BigDecimal;

@SpringBootTest
@ActiveProfiles({"jpa", "jpa-it"})
class TransferConcurrencyIT {

    @Autowired
    private TransferService transfers;

    @Autowired
    private SpringDataWalletRepository wallets;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TransactionTemplate tx;

    private void deleteAllRows() {
        jdbc.sql("DELETE FROM transfers").update();
        jdbc.sql("DELETE FROM wallets").update();
        jdbc.sql("DELETE FROM users").update();
    }

    @BeforeEach
    void clean() {
        deleteAllRows();
    }

    @AfterEach
    void cleanAfterTest() {
        deleteAllRows();
    }

    /**
     * Creates a user and a wallet, then sets the balance by raw UPDATE.
     *
     * <p>KEEP: the returned {@link Wallet} is STALE. The balance arrives via
     * JdbcClient, behind Hibernate's back, so the managed instance still holds
     * the 0.00 it was persisted with. Never assert on {@code returned.getBalance()}
     * — read the row back through {@code jdbc} instead, which is what the
     * assertion at the end of the race does.
     *
     * <p>The id is the only field here worth trusting, and it is all the race
     * actually needs.
     */
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

// ===========================================================================
//  DAY-10, STEP 1(ii) — THE FAILING TEST ITSELF.
//
//  Name it for the behaviour, not the mechanism:
//
//      @Test
//      void twoSimultaneousCreditsToOneWalletDoNotLoseMoney() throws Exception
//
//  ---------------------------------------------------------------------------
//  ARRANGE
//  ---------------------------------------------------------------------------
//
//  THREE wallets, not two. Both senders must be distinct, because a shared
//  sender would collide on the DEBIT — which is already safe — and you would
//  end up testing the half that works:
//
//      Wallet senderA  = fundedWallet("race-a@example.com", "100.00");
//      Wallet senderB  = fundedWallet("race-b@example.com", "100.00");
//      Wallet receiver = fundedWallet("race-r@example.com",   "0.00");
//
//  Pick two DIFFERENT amounts — say 30.00 and 50.00. Equal amounts would make
//  a lost update and a correct result arithmetically indistinguishable in some
//  orderings; different amounts make the final balance name its own bug.
//
//  ---------------------------------------------------------------------------
//  THE TWO LATCHES — why starting two threads is not enough
//  ---------------------------------------------------------------------------
//
//  If you just submit two tasks, the first may finish entirely before the
//  second begins. Then there is no overlap, no race, and a green test that
//  proves nothing. You must FORCE the interleaving:
//
//      CountDownLatch bothHaveRead = new CountDownLatch(2);   // threads → test
//      CountDownLatch release      = new CountDownLatch(1);   // test → threads
//
//      both threads:  begin tx, perform the READ of the receiver
//                     bothHaveRead.countDown()
//                     ... block on release ...
//      test thread:   await bothHaveRead   (now both hold the stale balance)
//                     release.countDown()
//      both threads:  write, commit
//
//  Run them on an ExecutorService of exactly 2 — a pool of 1 would serialise
//  them and you would deadlock on your own latch.
//
//  Capture each thread's outcome in its own AtomicReference<Throwable>. Do NOT
//  let an exception escape a Runnable: it would vanish into the executor and
//  the test would report a confusing timeout instead of the real cause.
//
//  🔴 AND COUNT THE LATCH DOWN ON THE FAILURE PATH TOO (a catch around the
//  whole tx block that both records the throwable AND calls
//  bothHaveRead.countDown()). If a thread dies before reaching its countDown,
//  the test thread waits out the full timeout and fails with "both threads
//  should have read" — pointing at the latch instead of at the actual
//  exception. That is a test failing for the wrong reason, which is the thing
//  you keep getting caught by: predict WHICH assertion fires, and make sure
//  the one that fires is the one that knows why.
//
//  Always use the TIMED overloads — await(10, TimeUnit.SECONDS) — never the
//  bare await(). A bare await turns a bug into a hung build with no output.
//
//  ---------------------------------------------------------------------------
//  ACT — the part that needs a decision from you
//  ---------------------------------------------------------------------------
//
//  Each thread must run its transfer inside tx.executeWithoutResult(status -> ...)
//  so it gets its own committing transaction. But transfer() is @Transactional
//  itself with REQUIRED, so it will JOIN the template's transaction — which is
//  what you want: one transaction per thread, spanning the whole transfer.
//
//  ⚠️ THE PROBLEM: you need both threads to pause AFTER the receiver has been
//  read but BEFORE the credit is written — and that point is in the MIDDLE of
//  transfer(), where a test cannot reach.
//
//  So choose, and write your choice as a comment above the test:
//
//    (a) RACE THE SERVICE. Both threads call transfers.transfer(...) and the
//        latch holds them only at the START of their transactions. Honest
//        end-to-end, but the collision is TIMING-DEPENDENT — the window
//        between findById and the commit flush is microseconds wide, so this
//        may well pass by luck and go flaky rather than red. A test that
//        fails only sometimes is worse than no test.
//
//    (b) RACE THE READ-MODIFY-WRITE DIRECTLY. Each thread, inside its own
//        tx template: wallets.findById(receiverId) → .credit(amount), with
//        the latch BETWEEN the findById and the end of the lambda (the flush
//        happens at commit, when the lambda returns). This reproduces the
//        exact shape of the credit half of transfer() and fails DETERMINISTIC-
//        ALLY, every run.
//
//        ⚠️ The cost: it drives the repository, not the service. That is
//        precisely the gap that let persona's retry mutation survive 93/93 —
//        an IT that bypasses the service cannot see service-level logic. Which
//        is exactly why step 5 exists as a separate unit test. Write that
//        trade-off down here; do not let it be an accident.
//
//  I would take (b) for this step and keep (a) as a second @Test you tolerate
//  being probabilistic — but it is your call, and the reason matters more than
//  the choice.
//
//  ---------------------------------------------------------------------------
//  ASSERT — the assertion is the whole point, so make it precise
//  ---------------------------------------------------------------------------
//
//  Read the final balance back through JdbcClient, NOT through the repository
//  (an entity cached in a stale persistence context would lie to you):
//
//      BigDecimal finalBalance = jdbc
//              .sql("SELECT balance FROM wallets WHERE id = ?")
//              .param(receiver.getId())
//              .query(BigDecimal.class)
//              .single();
//
//  Assert it equals the sum of BOTH credits — 80.00 on a 0.00 opening balance.
//
//  ⚠️ Use compareTo(...) == 0, not assertEquals, on BigDecimal. 80.00 and
//  80.0000 are equal in value and UNEQUAL under equals() because the scale
//  differs — and the wallets column is NUMERIC(19,4), so what comes back will
//  not have the scale you typed. You already know this one; it is the same
//  trap as the plain-string serialisation backstop.
//
//  Put the actual value in the failure message. "expected 80.00 but was 50.00"
//  tells you a credit was lost; a bare "assertion failed" tells you nothing.
//
//  EXPECT RED. The balance should come back as ONE of the two credits, not
//  their sum. Before you move to step 2, make sure you can answer: which
//  assertion failed, and does its message name the lost money?
//
//  ⚠️ And read WHERE it died. A red suite is not the same as a caught bug —
//  a mutation in persona went red inside @BeforeEach on a NOT NULL constraint,
//  so the race never ran at all and the "proof" was worthless. If this fails
//  during fundedWallet or on a FK, you have a setup problem, not a lost update.
// ===========================================================================

    /**
     * Concurrent credits to one wallet must be detected by optimistic locking.
     *
     * <h2>This races the repository, not {@code transfer()} — option (a)</h2>
     *
     * <p>The two {@link TransactionTemplate} transactions both read the same
     * receiver balance before either writes. The resulting version conflict
     * must reject exactly one credit; the persisted balance must reflect the
     * one that committed.
     *
     * <p>This deliberately calls the repository directly rather than
     * {@code transfers.transfer()}, so it proves that {@code @Version} detects
     * the conflict in isolation; it does not exercise the service retry loop.
     */
    @Test
    void twoSimultaneousCreditsToOneWalletDoNotLoseMoney() throws Exception {
        Wallet senderA = fundedWallet("race-a@example.com", "100.00");
        Wallet senderB = fundedWallet("race-b@example.com", "100.00");
        Wallet receiver = fundedWallet("race-r@example.com", "0.00");

        BigDecimal amountA = new BigDecimal("30.00");
        BigDecimal amountB = new BigDecimal("50.00");

        CountDownLatch bothHaveRead = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        AtomicReference<Throwable> failureA = new AtomicReference<>();
        AtomicReference<Throwable> failureB = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    Wallet managedReceiver = wallets.findById(receiver.getId())
                            .orElseThrow();

                    bothHaveRead.countDown();

                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("test did not release both transactions");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }

                    managedReceiver.credit(amountA);
                });
            } catch (Throwable throwable) {
                failureA.set(throwable);
                bothHaveRead.countDown();
            }
        });

        executor.submit(() -> {
            try {
                tx.executeWithoutResult(status -> {
                    Wallet managedReceiver = wallets.findById(receiver.getId())
                            .orElseThrow();

                    bothHaveRead.countDown();

                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("test did not release both transactions");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(exception);
                    }

                    managedReceiver.credit(amountB);
                });
            } catch (Throwable throwable) {
                failureB.set(throwable);
                bothHaveRead.countDown();
            }
        });

        boolean bothReadsCompleted = bothHaveRead.await(10, TimeUnit.SECONDS);

        release.countDown();
        executor.shutdown();

        assertTrue(
                executor.awaitTermination(10, TimeUnit.SECONDS),
                "both transactions should finish"
        );
        assertTrue(
                bothReadsCompleted,
                "both transactions should have read the same receiver balance"
        );

        assertTrue(
                (failureA.get() == null) != (failureB.get() == null),
                "exactly one transaction should fail; failureA=" + failureA.get()
                        + ", failureB=" + failureB.get()
        );

        Throwable conflict = failureA.get() != null ? failureA.get() : failureB.get();
        assertTrue(
                conflict instanceof ObjectOptimisticLockingFailureException,
                () -> "expected an optimistic-locking conflict, but was " + conflict
        );

        BigDecimal expectedBalance = failureA.get() != null ? amountB : amountA;
        BigDecimal finalBalance = jdbc
                .sql("SELECT balance FROM wallets WHERE id = ?")
                .param(receiver.getId())
                .query(BigDecimal.class)
                .single();

        assertEquals(
                0,
                finalBalance.compareTo(expectedBalance),
                () -> "expected receiver balance " + expectedBalance + " but was " + finalBalance
        );
    }

    @Test
    void simultaneousTransfersInOppositeDirectionsCompleteWithoutDeadlock() throws Exception {
        Wallet walletA = fundedWallet("deadlock-a@example.com", "100.00");
        Wallet walletB = fundedWallet("deadlock-b@example.com", "100.00");
        BigDecimal amount = new BigDecimal("10.00");

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failureA = new AtomicReference<>();
        AtomicReference<Throwable> failureB = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            bothReady.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting to start transfer A -> B");
                }
                transfers.transfer(walletA.getId(), walletB.getId(), amount);
            } catch (Throwable throwable) {
                failureA.set(throwable);
            }
        });

        executor.submit(() -> {
            bothReady.countDown();
            try {
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting to start transfer B -> A");
                }
                transfers.transfer(walletB.getId(), walletA.getId(), amount);
            } catch (Throwable throwable) {
                failureB.set(throwable);
            }
        });

        boolean bothReadyInTime = bothReady.await(10, TimeUnit.SECONDS);
        start.countDown();
        executor.shutdown();

        assertTrue(bothReadyInTime, "both transfer tasks should be ready");
        assertTrue(
                executor.awaitTermination(10, TimeUnit.SECONDS),
                "both transfer tasks should finish"
        );
        assertTrue(
                failureA.get() == null && failureB.get() == null,
                () -> "both transfers should complete; A -> B=" + failureA.get()
                        + ", B -> A=" + failureB.get()
        );

        BigDecimal balanceA = jdbc.sql("SELECT balance FROM wallets WHERE id = ?")
                .param(walletA.getId())
                .query(BigDecimal.class)
                .single();
        BigDecimal balanceB = jdbc.sql("SELECT balance FROM wallets WHERE id = ?")
                .param(walletB.getId())
                .query(BigDecimal.class)
                .single();

        BigDecimal total = balanceA.add(balanceB);
        assertEquals(
                0,
                total.compareTo(new BigDecimal("200.00")),
                () -> "wallet balances should conserve 200.00, but total was " + total
        );

        Long transferCount = jdbc.sql("SELECT COUNT(*) FROM transfers")
                .query(Long.class)
                .single();
        assertEquals(2L, transferCount, "exactly two transfers should be recorded");
    }
}