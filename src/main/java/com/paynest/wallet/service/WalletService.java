package com.paynest.wallet.service;

import com.paynest.user.model.User;
import com.paynest.user.repository.UserRepository;
import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.WalletRepository;
import com.paynest.wallet.exception.WalletFrozenException;
import com.paynest.wallet.model.WalletStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final UserRepository users;

    public WalletService(WalletRepository wallets, UserRepository users) {
        this.wallets = wallets;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Wallet getByEmail(String email) {
        User user = users.getByEmail(email);

        return wallets.findByUserId(user.getId())
                .orElseThrow(() -> new WalletNotFoundException(email));
    }

    private void assertMovementAllowed(Wallet wallet) {
        if (!wallet.getStatus().allowsMovement()) {
            throw new WalletFrozenException(wallet.getId(), wallet.getStatus());
        }
    }

    @Transactional
    public Wallet updateStatus(String email, WalletStatus newStatus) {
        Wallet wallet = getByEmail(email);
        wallet.changeStatus(newStatus);
        return wallet;
    }

    @Transactional
    public Wallet deposit(String email, BigDecimal amount) {
        Wallet wallet = getByEmail(email);
        assertMovementAllowed(wallet);
        wallet.credit(amount);
        return wallet;
    }

    @Transactional
    public Wallet createFor(User user) {
        return wallets.save(new Wallet(user));
    }
}