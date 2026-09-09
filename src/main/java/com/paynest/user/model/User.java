package com.paynest.user.model;

import lombok.Getter;
import lombok.Setter;
import lombok.AccessLevel;

import java.util.Optional;

@Getter
@Setter
public class User {

    private String firstName;
    private String lastName;

    //    username shall be the local of email
    private final String email;
    private String password;

    //    optional: null means no image set
    @Getter(AccessLevel.NONE)
    private String image;

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

    public String getUsername() {
        return "@" + firstName.toLowerCase() + "-" + lastName.toLowerCase();
    }

    public void setPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or blank");
        }
        this.password = password;
    }

    public Optional<String> getImage() {
        return Optional.ofNullable(image);
    }

    public void setImage(String image) {
        if (image != null && image.isBlank()) {
            throw new IllegalArgumentException("Image cannot be blank");
        }
        this.image = image;
    }
}