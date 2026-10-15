package com.paynest.wallet.repository;

import com.paynest.wallet.model.Wallet;

import java.math.BigDecimal;
import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findByUserId(Long userId);

    Wallet save(Wallet wallet);

    boolean existsByUserId(Long userId);

    int debitIfSufficient(Long walletId, BigDecimal amount);
}
