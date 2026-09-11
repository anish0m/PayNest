package com.paynest.user.exception;

public class UserNotFoundException extends RuntimeException {

    private final String email;

    public UserNotFoundException(String email) {
        super("No user found with email: " + email);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
