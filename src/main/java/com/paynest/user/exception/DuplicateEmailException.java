package com.paynest.user.exception;

public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("A user is already registered with email: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}