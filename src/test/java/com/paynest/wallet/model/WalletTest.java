package com.paynest.wallet.model;

import com.paynest.user.model.User;
import com.paynest.wallet.exception.WalletFrozenException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletTest {

    private static User user() {
        return new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
    }

    @Test
    void newWalletStartsAtZeroAndActive() {
        Wallet wallet = new Wallet(user());

        assertEquals(0, wallet.getBalance().compareTo(BigDecimal.ZERO));
        assertEquals(WalletStatus.ACTIVE, wallet.getStatus());
    }

    @Test
    void nullUserIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Wallet(null));
    }

    @Test
    void creditIncreasesBalance() {
        Wallet wallet = new Wallet(user());

        wallet.credit(new BigDecimal("50.00"));

        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("50.00")));
    }

    @Test
    void creditIsCumulative() {
        Wallet wallet = new Wallet(user());

        wallet.credit(new BigDecimal("50.00"));
        wallet.credit(new BigDecimal("25.00"));

        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("75.00")));
    }

    @Test
    void creditRejectsNull() {
        Wallet wallet = new Wallet(user());

        assertThrows(IllegalArgumentException.class, () -> wallet.credit(null));
    }

    @Test
    void creditRejectsZero() {
        Wallet wallet = new Wallet(user());

        assertThrows(IllegalArgumentException.class,
                () -> wallet.credit(new BigDecimal("0.00")));
    }

    @Test
    void creditRejectsNegative() {
        Wallet wallet = new Wallet(user());

        assertThrows(IllegalArgumentException.class,
                () -> wallet.credit(new BigDecimal("-5.00")));
    }

    @Test
    void creditPreservesScale() {
        Wallet wallet = new Wallet(user());

        wallet.credit(new BigDecimal("50.00"));

        assertEquals(2, wallet.getBalance().scale());
    }

    @Test
    void activeToFrozenIsAllowed() {
        Wallet wallet = new Wallet(user());

        wallet.changeStatus(WalletStatus.FROZEN);

        assertEquals(WalletStatus.FROZEN, wallet.getStatus());
    }

    @Test
    void frozenToActiveIsAllowed() {
        Wallet wallet = new Wallet(user());

        wallet.changeStatus(WalletStatus.FROZEN);
        wallet.changeStatus(WalletStatus.ACTIVE);

        assertEquals(WalletStatus.ACTIVE, wallet.getStatus());
    }

    @Test
    void activeToClosedIsAllowed() {
        Wallet wallet = new Wallet(user());

        wallet.changeStatus(WalletStatus.CLOSED);

        assertEquals(WalletStatus.CLOSED, wallet.getStatus());
    }

    @Test
    void closedToActiveIsRejected() {
        Wallet wallet = new Wallet(user());

        wallet.changeStatus(WalletStatus.CLOSED);

        assertThrows(WalletFrozenException.class,
                () -> wallet.changeStatus(WalletStatus.ACTIVE));
    }

    @Test
    void closedToClosedIsAllowed() {
        Wallet wallet = new Wallet(user());

        wallet.changeStatus(WalletStatus.CLOSED);
        wallet.changeStatus(WalletStatus.CLOSED);

        assertEquals(WalletStatus.CLOSED, wallet.getStatus());
    }

    @Test
    void nullStatusIsRejected() {
        Wallet wallet = new Wallet(user());

        assertThrows(IllegalArgumentException.class,
                () -> wallet.changeStatus(null));
    }

    @Test
    void walletStatusAllowsMovementOnlyForActive() {
        assertTrue(WalletStatus.ACTIVE.allowsMovement());
        assertFalse(WalletStatus.FROZEN.allowsMovement());
        assertFalse(WalletStatus.CLOSED.allowsMovement());
    }
}