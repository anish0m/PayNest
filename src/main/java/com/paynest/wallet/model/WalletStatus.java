package com.paynest.wallet.model;

public enum WalletStatus {
    ACTIVE,
    FROZEN,
    CLOSED;

    public boolean allowsMovement() {
        return this == ACTIVE;
    }
}