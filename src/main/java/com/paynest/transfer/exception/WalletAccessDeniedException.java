package com.paynest.transfer.exception;

public final class WalletAccessDeniedException extends RuntimeException {

    private final Long walletId;

    public WalletAccessDeniedException(Long walletId) {
        super("Access denied for wallet " + walletId);
        this.walletId = walletId;
    }

    public Long getWalletId() {
        return walletId;
    }
}