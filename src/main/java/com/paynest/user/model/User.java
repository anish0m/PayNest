package com.paynest.user.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {

    private String firstName;
    private String lastName;

    //    username shall be the local of email
    private final String email;
    private String password;

    //    constructor
    public User(String firstName, String lastName, String email, String password) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("firstName cannot be null or blank");
        }
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("lastName cannot be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }

        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        setPassword(password);
    }

    public void setPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or blank");
        }
        this.password = password;
    }
}