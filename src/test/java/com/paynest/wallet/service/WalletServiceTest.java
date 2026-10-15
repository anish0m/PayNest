package com.paynest.wallet.service;

import com.paynest.user.model.User;
import com.paynest.user.repository.InMemoryUserRepository;
import com.paynest.user.repository.UserRepository;
import com.paynest.wallet.exception.WalletFrozenException;
import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.model.WalletStatus;
import com.paynest.wallet.repository.InMemoryWalletRepository;
import com.paynest.wallet.repository.WalletRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WalletServiceTest {

    private UserRepository users;
    private WalletRepository wallets;
    private WalletService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        wallets = new InMemoryWalletRepository();
        service = new WalletService(wallets, users);
    }

    private Wallet walletFor(String email) {
        User user = users.save(
                new User("Anishom", "Frost", email, "Pass1234#"));

        return wallets.save(new Wallet(user));
    }

    @Test
    void depositOnAFrozenWalletIsRejected() {
        String email = "frozen@example.com";
        Wallet wallet = walletFor(email);

        service.updateStatus(email, WalletStatus.FROZEN);

        assertThrows(
                WalletFrozenException.class,
                () -> service.deposit(email, new BigDecimal("50.00")));

        assertEquals(
                0,
                wallet.getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void depositIncreasesTheBalance() {
        String email = "deposit@example.com";
        walletFor(email);

        Wallet deposited = service.deposit(email, new BigDecimal("50.00"));

        assertEquals(
                0,
                deposited.getBalance().compareTo(new BigDecimal("50.00")));
    }

    @Test
    void depositOnAMissingWalletThrowsWalletNotFound() {
        String email = "missing-wallet@example.com";

        users.save(new User("Anishom", "Frost", email, "Pass1234#"));

        assertThrows(
                WalletNotFoundException.class,
                () -> service.deposit(email, new BigDecimal("50.00")));
    }

    @Test
    void closedWalletCannotBeReactivated() {
        String email = "closed@example.com";
        walletFor(email);

        service.updateStatus(email, WalletStatus.CLOSED);

        assertThrows(
                WalletFrozenException.class,
                () -> service.updateStatus(email, WalletStatus.ACTIVE));
    }
}
