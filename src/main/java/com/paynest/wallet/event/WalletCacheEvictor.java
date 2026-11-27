package com.paynest.wallet.event;

import com.paynest.config.CacheConfig;

import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.repository.WalletRepository;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WalletCacheEvictor {

    private final WalletRepository wallets;
    private final CacheManager cacheManager;

    public WalletCacheEvictor(WalletRepository wallets, CacheManager cacheManager) {
        this.wallets = wallets;
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onWalletChanged(WalletChangedEvent event) {
        String email = wallets.findById(event.walletId())
                .orElseThrow(() -> new WalletNotFoundException(event.walletId()))
                .getUser()
                .getEmail();

        cacheManager.getCache(CacheConfig.WALLET_BALANCE).evict(email);
    }
}