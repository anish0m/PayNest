package com.paynest.transfer.service;

import com.paynest.transfer.repository.TransferRepository;
import com.paynest.transfer.repository.InMemoryTransferRepository;
import com.paynest.transfer.exception.SelfTransferException;
import com.paynest.transfer.exception.InsufficientFundsException;
import com.paynest.transfer.model.Transfer;
import com.paynest.transfer.model.TransferStatus;

import com.paynest.user.repository.InMemoryUserRepository;
import com.paynest.user.repository.UserRepository;
import com.paynest.user.model.User;

import com.paynest.wallet.repository.WalletRepository;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.InMemoryWalletRepository;
import com.paynest.wallet.exception.WalletFrozenException;
import com.paynest.wallet.model.WalletStatus;
import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.service.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

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

}