package com.paynest.transfer.service;

import com.paynest.transfer.dto.TransferFilter;
import com.paynest.transfer.dto.TransferResponse;
import com.paynest.transfer.exception.InsufficientFundsException;
import com.paynest.transfer.exception.SelfTransferException;
import com.paynest.transfer.exception.TransferNotFoundException;
import com.paynest.transfer.exception.WalletAccessDeniedException;
import com.paynest.transfer.model.Transfer;
import com.paynest.transfer.model.TransferDirection;
import com.paynest.transfer.model.TransferStatus;
import com.paynest.transfer.repository.InMemoryTransferRepository;
import com.paynest.transfer.repository.TransferRepository;

import com.paynest.user.model.User;
import com.paynest.user.repository.InMemoryUserRepository;
import com.paynest.user.repository.UserRepository;

import com.paynest.wallet.exception.WalletFrozenException;
import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.model.WalletStatus;
import com.paynest.wallet.repository.InMemoryWalletRepository;
import com.paynest.wallet.repository.WalletRepository;
import com.paynest.wallet.service.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;

import java.util.Optional;
import java.util.UUID;

class TransferServiceTest {

    private WalletRepository wallets;
    private TransferRepository transfers;
    private TransferService service;
    private UserRepository users;

    // KEEP: The 200-wallet loop forces the self-transfer wallet id above 127,
    // outside the Long cache; otherwise replacing Objects.equals with == passes.
    // Mutation verified on 19 Oct 2026: selfTransferIsRejectedWithoutChangingTheBalance
    // failed with "Expected SelfTransferException to be thrown, but nothing was thrown".
    // Do not shrink this loop or lower its bound.

    private Wallet walletWithIdAboveLongCache(String email, String balance) {
        for (int index = 0; index < 200; index++) {
            walletWith("unused-" + index + "@example.com", "0.01");
        }

        Wallet wallet = walletWith(email, balance);

        assertTrue(wallet.getId() > 127L);

        return wallet;
    }

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        wallets = new InMemoryWalletRepository();
        transfers = new InMemoryTransferRepository();

        WalletService walletService =
                new WalletService(wallets, users);

        service = new TransferService(
                wallets,
                transfers,
                walletService);
    }

    private Wallet walletWith(String email, String balance) {
        User user = users.save(
                new User("Anishom", "Frost", email, "Pass1234#"));

        Wallet wallet = wallets.save(new Wallet(user));
        wallet.credit(new BigDecimal(balance));

        return wallets.save(wallet);
    }

    @Test
    void transferMovesMoneyWithoutCreatingOrDestroyingIt() {
        Wallet sender = walletWith("sender@example.com", "100.00");
        Wallet receiver = walletWith("receiver@example.com", "40.00");
        BigDecimal amount = new BigDecimal("25.00");

        BigDecimal totalBefore = sender.getBalance().add(receiver.getBalance());

        service.transfer(sender.getId(), receiver.getId(), amount);

        BigDecimal totalAfter = sender.getBalance().add(receiver.getBalance());

        assertEquals(
                0,
                sender.getBalance().compareTo(new BigDecimal("75.00")));
        assertEquals(
                0,
                receiver.getBalance().compareTo(new BigDecimal("65.00")));
        assertEquals(0, totalAfter.compareTo(totalBefore));
    }

    @Test
    void selfTransferIsRejectedWithoutChangingTheBalance() {
        Wallet wallet = walletWithIdAboveLongCache(
                "self-transfer@example.com",
                "100.00");
        BigDecimal balanceBefore = wallet.getBalance();

        // KEEP: Deliberately create two distinct Long instances; see the large-id
        // loop KEEP above. Do not use wallet.getId() twice, or == compares one reference to itself.

        Long senderWalletId = Long.valueOf(wallet.getId().longValue());
        Long receiverWalletId = Long.valueOf(wallet.getId().longValue());

        assertThrows(
                SelfTransferException.class,
                () -> service.transfer(
                        senderWalletId,
                        receiverWalletId,
                        new BigDecimal("25.00")));

        assertEquals(0, wallet.getBalance().compareTo(balanceBefore));
    }

    @Test
    void insufficientFundsLeaveBothBalancesUnchanged() {
        Wallet sender = walletWith("poor-sender@example.com", "20.00");
        Wallet receiver = walletWith("receiver@example.com", "80.00");
        BigDecimal senderBalanceBefore = sender.getBalance();
        BigDecimal receiverBalanceBefore = receiver.getBalance();

        assertThrows(
                InsufficientFundsException.class,
                () -> service.transfer(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("100.00")));

        assertEquals(0, sender.getBalance().compareTo(senderBalanceBefore));
        assertEquals(0, receiver.getBalance().compareTo(receiverBalanceBefore));
    }

    @Test
    void frozenSenderIsRejectedWithoutChangingBalances() {
        Wallet sender = walletWith("frozen-sender@example.com", "100.00");
        Wallet receiver = walletWith("receiver@example.com", "40.00");
        BigDecimal senderBalanceBefore = sender.getBalance();
        BigDecimal receiverBalanceBefore = receiver.getBalance();

        sender.changeStatus(WalletStatus.FROZEN);

        assertThrows(
                WalletFrozenException.class,
                () -> service.transfer(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("25.00")));

        assertEquals(0, sender.getBalance().compareTo(senderBalanceBefore));
        assertEquals(0, receiver.getBalance().compareTo(receiverBalanceBefore));
    }

    @Test
    void frozenReceiverIsRejectedWithoutChangingBalances() {
        Wallet sender = walletWith("sender@example.com", "100.00");
        Wallet receiver = walletWith("frozen-receiver@example.com", "40.00");
        BigDecimal senderBalanceBefore = sender.getBalance();
        BigDecimal receiverBalanceBefore = receiver.getBalance();

        receiver.changeStatus(WalletStatus.FROZEN);

        assertThrows(
                WalletFrozenException.class,
                () -> service.transfer(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("25.00")));

        assertEquals(0, sender.getBalance().compareTo(senderBalanceBefore));
        assertEquals(0, receiver.getBalance().compareTo(receiverBalanceBefore));
    }

    @Test
    void missingSenderThrowsWalletNotFoundException() {
        Wallet receiver = walletWith("receiver@example.com", "40.00");

        assertThrows(
                WalletNotFoundException.class,
                () -> service.transfer(
                        999L,
                        receiver.getId(),
                        new BigDecimal("25.00")));
    }

    @Test
    void missingReceiverThrowsWalletNotFoundException() {
        Wallet sender = walletWith("sender@example.com", "100.00");

        assertThrows(
                WalletNotFoundException.class,
                () -> service.transfer(
                        sender.getId(),
                        999L,
                        new BigDecimal("25.00")));
    }

    @Test
    void transferReturnsACompletedRecordWithTheCorrectDetails() {
        Wallet sender = walletWith("sender@example.com", "100.00");
        Wallet receiver = walletWith("receiver@example.com", "30.00");
        BigDecimal amount = new BigDecimal("25.00");

        Transfer transfer = service.transfer(
                sender.getId(),
                receiver.getId(),
                amount);

        assertEquals(TransferStatus.COMPLETED, transfer.getStatus());
        assertEquals(0, transfer.getAmount().compareTo(amount));
        assertEquals(sender.getId(), transfer.getSenderWalletId());
        assertEquals(receiver.getId(), transfer.getReceiverWalletId());
    }

    @Test
    void detailsReturnsTheTransferForTheSender() {
        Wallet sender = walletWith("sender@example.com", "100.00");
        Wallet receiver = walletWith("receiver@example.com", "30.00");

        Transfer transfer = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        Transfer result = service.findById(
                transfer.getId(),
                "sender@example.com");

        assertEquals(transfer.getId(), result.getId());
    }

    @Test
    void detailsReturnsTheTransferForTheReceiver() {
        Wallet sender = walletWithIdAboveLongCache(
                "sender@example.com",
                "100.00");
        Wallet receiver = walletWith("receiver@example.com", "30.00");

        Transfer transfer = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        Transfer result = service.findById(
                transfer.getId(),
                "receiver@example.com");

        assertEquals(transfer.getId(), result.getId());
    }

    @Test
    void detailsIsRejectedForAWalletOnNeitherSide() {
        Wallet sender = walletWith("sender@example.com", "100.00");
        Wallet receiver = walletWith("receiver@example.com", "30.00");

        walletWith("outsider@example.com", "50.00");

        Transfer transfer = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        assertThrows(
                WalletAccessDeniedException.class,
                () -> service.findById(
                        transfer.getId(),
                        "outsider@example.com"));
    }

    @Test
    void detailsThrowsTransferNotFoundForAnUnknownId() {
        walletWith("sender@example.com", "100.00");

        assertThrows(
                TransferNotFoundException.class,
                () -> service.findById(
                        UUID.randomUUID(),
                        "sender@example.com"));
    }

    @Test
    void directionIsSentWhenTheViewerIsTheSender() {
        Wallet sender = walletWith(
                "direction-sender-1@example.com",
                "100.00");

        Wallet receiver = walletWith(
                "direction-receiver-1@example.com",
                "30.00");

        Transfer transfer = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        TransferResponse response = TransferResponse.from(
                transfer,
                sender.getId());

        assertEquals(TransferDirection.SENT, response.direction());
    }

    @Test
    void directionIsReceivedWhenTheViewerIsTheReceiver() {
        Wallet sender = walletWith(
                "direction-sender-2@example.com",
                "100.00");

        Wallet receiver = walletWith(
                "direction-receiver-2@example.com",
                "30.00");

        Transfer transfer = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        TransferResponse response = TransferResponse.from(
                transfer,
                receiver.getId());

        assertEquals(TransferDirection.RECEIVED, response.direction());
    }

    @Test
    void theSameTransferRendersBothWaysForItsTwoParties() {
        Wallet sender = walletWith(
                "direction-sender-3@example.com",
                "100.00");

        Wallet receiver = walletWith(
                "direction-receiver-3@example.com",
                "30.00");

        Transfer transfer = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        TransferResponse asSender = TransferResponse.from(
                transfer,
                sender.getId());

        TransferResponse asReceiver = TransferResponse.from(
                transfer,
                receiver.getId());

        assertEquals(TransferDirection.SENT, asSender.direction());
        assertEquals(TransferDirection.RECEIVED, asReceiver.direction());
        assertEquals(asSender.id(), asReceiver.id());
    }

    @Test
    void directionIsSentOnTheResponseReturnedByATransfer() {
        Wallet sender = walletWith(
                "direction-sender-4@example.com",
                "100.00");

        Wallet receiver = walletWith(
                "direction-receiver-4@example.com",
                "30.00");

        Transfer transfer = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        TransferResponse response = TransferResponse.from(
                transfer,
                sender.getId());

        assertEquals(TransferDirection.SENT, response.direction());
    }

    @Test
    void emptyFilterReturnsEveryTransferForTheWallet() {
        Wallet sender = walletWith("filter-1-sender@example.com", "100.00");
        Wallet receiver = walletWith("filter-1-receiver@example.com", "10.00");

        service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("10.00"));

        service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("20.00"));

        service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("30.00"));

        TransferFilter filter = new TransferFilter(
                null,
                null,
                null,
                null,
                null);

        var page = service.history(
                sender.getId(),
                "filter-1-sender@example.com",
                filter,
                PageRequest.of(0, 20));

        assertFalse(page.isEmpty());
        assertEquals(3, page.getTotalElements());
        assertEquals(3, page.getContent().size());
    }

    @Test
    void statusFilterExcludesTransfersWithANonMatchingStatus() {
        Wallet sender = walletWith("filter-2-sender@example.com", "100.00");
        Wallet receiver = walletWith("filter-2-receiver@example.com", "10.00");

        service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        TransferFilter noFilter = new TransferFilter(
                null,
                null,
                null,
                null,
                null);

        var allTransfers = service.history(
                sender.getId(),
                "filter-2-sender@example.com",
                noFilter,
                PageRequest.of(0, 20));

        TransferFilter pendingOnly = new TransferFilter(
                TransferStatus.PENDING,
                null,
                null,
                null,
                null);

        var pendingTransfers = service.history(
                sender.getId(),
                "filter-2-sender@example.com",
                pendingOnly,
                PageRequest.of(0, 20));

        assertFalse(allTransfers.isEmpty());
        assertEquals(1, allTransfers.getTotalElements());
        assertTrue(pendingTransfers.isEmpty());
        assertEquals(0, pendingTransfers.getTotalElements());
    }

    @Test
    void amountRangeIncludesTransfersOnBothInclusiveBoundaries() {
        Wallet sender = walletWith("filter-3-sender@example.com", "100.00");
        Wallet receiver = walletWith("filter-3-receiver@example.com", "10.00");

        Transfer belowRange = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("10.00"));

        Transfer lowerBoundary = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("25.00"));

        Transfer upperBoundary = service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("50.00"));

        TransferFilter filter = new TransferFilter(
                null,
                null,
                null,
                new BigDecimal("25.00"),
                new BigDecimal("50.00"));

        var page = service.history(
                sender.getId(),
                "filter-3-sender@example.com",
                filter,
                PageRequest.of(0, 20));

        assertFalse(page.isEmpty());
        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().anyMatch(
                transfer -> transfer.getId().equals(lowerBoundary.getId())));
        assertTrue(page.getContent().stream().anyMatch(
                transfer -> transfer.getId().equals(upperBoundary.getId())));
        assertTrue(page.getContent().stream().noneMatch(
                transfer -> transfer.getId().equals(belowRange.getId())));
    }

    // ======================================================================
    // Day-10 step 5 — the test for the retry. THREE sub-steps, in order.
    //
    // WHY this step exists at all: your own run of the (c) mutation already
    // proved the gap. With MAX_TRANSFER_ATTEMPTS lowered 3 -> 1 — which
    // deletes the retry outright and makes every losing sender see an error —
    // the remaining 105 tests PASSED. Nothing in this repo can currently tell
    // a retrying transfer from a non-retrying one. Step 4 shipped behaviour
    // the build does not hold.
    //
    // WHERE the conflict actually comes from, because the fake has to imitate
    // it in the right place. @Version on Wallet does NOT guard the debit:
    // debitIfSufficient is a single conditional UPDATE, which cannot lose a
    // read-modify-write it never performs. The guarded side is the CREDIT —
    // findById(receiver) -> credit() -> dirty-check flush at commit, a
    // read-modify-write with a gap in the middle. So the fake throws from
    // findById, and specifically from the RECEIVER re-read (the one at
    // TransferService.java:121), not from the first two lookups. Throw it
    // earlier and the attempt dies before the debit, which would make
    // sub-step 5(iii) — the assertion that actually matters — untestable.
    //
    // ----------------------------------------------------------------------
    // 5(i)  The fake. A private static final class at the bottom of this
    //       class, EXTENDING InMemoryWalletRepository rather than mocking the
    //       interface — subclassing keeps save/findByUserId/debitIfSufficient
    //       behaving exactly as they do in the 20-odd tests above, so only the
    //       one behaviour under test changes. Pattern to copy:
    //       anthropic-persona-monolithic/.../UserServiceTest.java:368
    //       (ConflictOnceRepository).
    //
    //       Differences from persona's version, both forced by the above:
    //         - override findById, not update
    //         - conflict only when the id is the receiver's, and only after
    //           the debit has happened; a counter of calls for that id is
    //           enough, since the receiver is looked up twice per attempt
    //           (line 90 then line 121) and it is the SECOND you want
    //         - expose the attempt count, as persona does, so 5(ii) can
    //           assert the loop ran the number of times you think it did
    //       Constructor takes how many conflicts to throw. Throw
    //       new ObjectOptimisticLockingFailureException(Wallet.class, id).

    private static final class ConflictInjectingWalletRepository
            extends InMemoryWalletRepository {

        private final Long receiverWalletId;
        private final int conflictsToThrow;
        private int receiverReads;
        private int conflictsThrown;
        private int attemptCount;

        private ConflictInjectingWalletRepository(
                Long receiverWalletId,
                int conflictsToThrow) {
            this.receiverWalletId = receiverWalletId;
            this.conflictsToThrow = conflictsToThrow;
        }

        @Override
        public Optional<Wallet> findById(Long id) {
            if (receiverWalletId.equals(id)) {
                receiverReads++;

                // The receiver is read twice per attempt; inject the conflict
                // on the second read, after the sender has been debited.
                if (receiverReads % 2 == 0
                        && conflictsThrown < conflictsToThrow) {
                    conflictsThrown++;
                    throw new ObjectOptimisticLockingFailureException(
                            Wallet.class, id);
                }
            }

            return super.findById(id);
        }

        @Override
        public int debitIfSufficient(Long walletId, BigDecimal amount) {
            attemptCount++;
            return super.debitIfSufficient(walletId, amount);
        }

        private int attemptCount() {
            return attemptCount;
        }
    }

    // 5(ii) Two tests, and they are a PAIR — neither is worth much alone.
    //         (a) conflictsToThrow = 1: transfer SUCCEEDS, and assert the
    //             attempt count is 2. Without the count this passes against a
    //             service that never conflicted at all.
    //         (b) conflictsToThrow = 99: assertThrows
    //             ObjectOptimisticLockingFailureException, and assert the
    //             count is exactly 3. Without this, (a) also passes against
    //             an INFINITE loop — which turns a busy wallet into a hang
    //             rather than an error. Hard-code 3; it is the contract.

    @Test
    void transferRetriesOnceAndSucceedsOnSecondAttempt() {
        ConflictInjectingWalletRepository conflictWallets =
                serviceWithConflicts(1);

        Wallet sender = walletWith("retry-sender@example.com", "100.00");
        Wallet receiver = walletWith("retry-receiver@example.com", "40.00");

        service.transfer(
                sender.getId(),
                receiver.getId(),
                new BigDecimal("10.00"));

        assertEquals(2, conflictWallets.attemptCount());
    }

    @Test
    void transferStopsAfterThreeAttemptsWhenConflictsPersist() {
        ConflictInjectingWalletRepository conflictWallets =
                serviceWithConflicts(99);

        Wallet sender = walletWith("conflict-sender@example.com", "100.00");
        Wallet receiver = walletWith("conflict-receiver@example.com", "40.00");

        assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> service.transfer(
                        sender.getId(),
                        receiver.getId(),
                        new BigDecimal("10.00")));

        assertEquals(3, conflictWallets.attemptCount());
    }

    private ConflictInjectingWalletRepository serviceWithConflicts(
            int conflictsToThrow) {
        users = new InMemoryUserRepository();
        ConflictInjectingWalletRepository conflictWallets =
                new ConflictInjectingWalletRepository(2L, conflictsToThrow);
        wallets = conflictWallets;
        transfers = new InMemoryTransferRepository();

        WalletService walletService = new WalletService(wallets, users);
        service = new TransferService(wallets, transfers, walletService);

        return conflictWallets;
    }

    // 5(iii) The double-debit assertion. This is the one to write carefully,
    //        because it is the only test here guarding real money: after the
    //        retried-and-succeeded transfer of (a), the sender's balance must
    //        have gone down by the amount ONCE, not twice.
    //
    //        ⚠️ AND IT IS THE ONE THIS FAKE CANNOT HONESTLY PROVE. In
    //        production, attempt 1's debit is undone because the exception
    //        escapes attemptTransfer and the proxy rolls back. The fake has no
    //        transaction: InMemoryWalletRepository.findById hands back the
    //        LIVE shared Wallet instance (line 54 — no copy), and
    //        debitIfSufficient mutates it in place, so attempt 1's debit
    //        STAYS. Write the assertion against the honest expectation — down
    //        by the amount once — and expect it to FAIL at first, showing the
    //        balance short by two amounts. That failure is a true statement
    //        about the fake, not about your code.
    //
    //        So this sub-step is a decision, not just code. Write the test,
    //        watch it fail, then pick one and say which in the comment you
    //        leave behind:
    //          - move the assertion to TransferServiceIT, where a real
    //            rollback exists and the claim is actually verifiable; leave
    //            a KEEP comment here saying why it is NOT a unit test, or
    //          - have the fake snapshot/restore the balance when it throws,
    //            i.e. model the rollback — honest only if the comment says
    //            the fake is imitating the proxy
    //        Do not delete the assertion. An untestable guarantee about money
    //        is a finding, and it belongs written down either way.

}
