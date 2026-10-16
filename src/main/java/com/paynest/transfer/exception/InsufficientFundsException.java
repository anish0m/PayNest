package com.paynest.transfer.exception;

import java.math.BigDecimal;

public final class InsufficientFundsException extends RuntimeException {

    private final Long walletId;

    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientFundsException(
            Long walletId,
            BigDecimal requested,
            BigDecimal available) {

        super("Insufficient funds in wallet " + walletId
                + ": requested " + requested
                + ", available " + available);

        this.walletId = walletId;
        this.requested = requested;
        this.available = available;
    }

    public Long getWalletId() {
        return walletId;
    }

    public BigDecimal getRequested() {
        return requested;
    }

    public BigDecimal getAvailable() {
        return available;
    }

}
