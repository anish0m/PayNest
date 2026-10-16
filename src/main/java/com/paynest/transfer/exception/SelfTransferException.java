package com.paynest.transfer.exception;

public final class SelfTransferException extends RuntimeException {

    private final Long walletId;

    public SelfTransferException(Long walletId) {
        super("Wallet " + walletId + " cannot transfer to itself");
        this.walletId = walletId;
    }

    public Long getWalletId() {
        return walletId;
    }

}
