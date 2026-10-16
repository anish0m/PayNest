package com.paynest.wallet.repository;

import com.paynest.wallet.model.Wallet;

import jakarta.persistence.EntityManager;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
@Profile({"jpa", "jdbc"})
public class JpaWalletRepository implements WalletRepository {

    private final SpringDataWalletRepository wallets;
    private final EntityManager entityManager;

    public JpaWalletRepository(SpringDataWalletRepository wallets,
                               EntityManager entityManager) {
        this.wallets = wallets;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Wallet> findByUserId(Long userId) {
        return wallets.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Wallet> findById(Long id) {
        return wallets.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserId(Long userId) {
        return wallets.existsByUserId(userId);
    }

    @Override
    @Transactional
    public int debitIfSufficient(Long walletId, BigDecimal amount) {
        return wallets.debitIfSufficient(walletId, amount);
    }

    // Duplicate wallets indicate an internal bug, so the database exception remains a 500.
    @Override
    @Transactional
    public Wallet save(Wallet wallet) {
        Wallet saved = wallets.save(wallet);

        wallets.flush();
        entityManager.refresh(saved);

        return saved;
    }

}
