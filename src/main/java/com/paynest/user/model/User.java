package com.paynest.user.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Optional;

@Entity
@Table(name = "users")
@Getter
@Setter
@ToString(exclude = "password")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @Column(name = "id")
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private java.time.Instant createdAt;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Setter(AccessLevel.NONE)
    @Column(name = "email", nullable = false, length = 255, unique = true, updatable = false)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "image", length = 512)
    private String image;

    protected User() {
    }

    //    constructor
    public User(String firstName, String lastName, String email, String password) {
        requireText(email, "Email");
        setFirstName(firstName);
        setLastName(lastName);
        this.email = email;
        setPassword(password);
    }

    public User(Long id, String firstName, String lastName, String email,
                String password, String image, java.time.Instant createdAt) {
        this(firstName, lastName, email, password);
        this.id = id;
        this.image = image;
        this.createdAt = createdAt;
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

    //    image getter
    public Optional<String> getImage() {
        return Optional.ofNullable(image);
    }

    //    image setter
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