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
        requireText(email, "Email");

        setFirstName(firstName);
        setLastName(lastName);
        this.email = email;
        setPassword(password);
    }

    //    auto username from firstname and lastname
    public String getUsername() {
        return "@" + firstName.toLowerCase() + "-" + lastName.toLowerCase();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }

    // firstname setter
    public void setFirstName(String firstName) {
        requireText(firstName, "First Name");
        this.firstName = firstName;
    }

    // lastname setter
    public void setLastName(String lastName) {
        requireText(lastName, "Last Name");
        this.lastName = lastName;
    }

    //    password setter
    public void setPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or blank");
        }
        this.password = password;
    }

    //    image setter
    public Optional<String> getImage() {
        return Optional.ofNullable(image);
    }

    //    image getter
    public void setImage(String image) {
        if (image != null && image.isBlank()) {
            throw new IllegalArgumentException("Image cannot be blank");
        }
        this.image = image;
    }

    //    equals
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;

        if (other == null || getClass() != other.getClass()) return false;

        User user = (User) other;

        return email.equals(user.email);
    }

    @Override
    public int hashCode() {
        return email.hashCode();
    }

}