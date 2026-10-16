package com.paynest.wallet.exception;

public class WalletNotFoundException extends RuntimeException {

    private final String email;

    public WalletNotFoundException(Long walletId) {
        super("No wallet found with id: " + walletId);
        this.email = null;
        this.walletId = walletId;
    }

    public String getEmail() {
        return email;
    }

    private final Long walletId;

    public WalletNotFoundException(String email) {
        super("No wallet found for user: " + email);
        this.email = email;
        this.walletId = null;
    }

    public Long getWalletId() {
        return walletId;
    }
}