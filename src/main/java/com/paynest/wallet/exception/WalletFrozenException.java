package com.paynest.wallet.exception;

import com.paynest.wallet.model.WalletStatus;

public class WalletFrozenException extends RuntimeException {

    private final Long walletId;
    private final WalletStatus status;

    public WalletFrozenException(Long walletId, WalletStatus status) {
        super("Wallet " + walletId + " is " + status + " and cannot accept movement");
        this.walletId = walletId;
        this.status = status;
    }

    public Long getWalletId() {
        return walletId;
    }

    public WalletStatus getStatus() {
        return status;
    }
}
