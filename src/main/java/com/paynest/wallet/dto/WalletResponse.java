package com.paynest.wallet.dto;

import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.model.WalletStatus;

import java.time.Instant;

public record WalletResponse(
        Long id,
        String balance,
        WalletStatus status,
        Instant createdAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getBalance().toPlainString(),
                wallet.getStatus(),
                wallet.getCreatedAt());
    }
}
