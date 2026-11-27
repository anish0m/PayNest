package com.paynest.wallet.service;

import com.paynest.config.CacheConfig;

import com.paynest.user.model.User;
import com.paynest.user.repository.UserRepository;

import com.paynest.wallet.dto.WalletResponse;
import com.paynest.wallet.event.WalletChangedEvent;
import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.WalletRepository;
import com.paynest.wallet.exception.WalletFrozenException;
import com.paynest.wallet.model.WalletStatus;

import java.math.BigDecimal;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public WalletService(
            WalletRepository wallets,
            UserRepository users,
            ApplicationEventPublisher events) {
        this.wallets = wallets;
        this.users = users;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Wallet getByEmail(String email) {
        User user = users.getByEmail(email);

        return wallets.findByUserId(user.getId())
                .orElseThrow(() -> new WalletNotFoundException(email));
    }

    // Cache a DTO for reads; getByEmail stays a managed entity lookup for mutations.
    @Cacheable(cacheNames = CacheConfig.WALLET_BALANCE)
    public WalletResponse getBalance(String email) {
        return WalletResponse.from(getByEmail(email));
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
        events.publishEvent(new WalletChangedEvent(wallet.getId()));
        return wallet;
    }

    @Transactional
    public Wallet deposit(String email, BigDecimal amount) {
        Wallet wallet = getByEmail(email);
        assertMovementAllowed(wallet);
        wallet.credit(amount);
        events.publishEvent(new WalletChangedEvent(wallet.getId()));
        return wallet;
    }

    @Transactional
    public Wallet createFor(User user) {
        return wallets.save(new Wallet(user));
    }
}
