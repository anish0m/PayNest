package com.paynest.wallet.exception;

public class WalletNotFoundException extends RuntimeException {

    private final String email;

    public WalletNotFoundException(String email) {
        super("No wallet found for user: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}