package com.paynest.user.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    @Test
    void emailIsWhatWePassedIn() {
        User user = new User("Mohsina", "Tabassum", "m@example.com", "secret123");
        assertEquals("m@example.com", user.getEmail());
    }

    @Test
    void firstNameCanBeChanged() {
        User user = new User("Mohsina", "Tabassum", "m@example.com", "secret123");
        user.setFirstName("Turhan");
        assertEquals("Turhan", user.getFirstName());
    }

    @Test
    void toStringDoesNotLeakThePassword() {
        User user = new User("Mohsina", "Tabassum", "m@example.com", "secret123");
        assertFalse(user.toString().contains("secret123"));
    }

    @Test
    void blankEmailIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new User("Mohsina", "Tabassum", "", "secret123"));
    }

    @Test
    void blankPasswordIsRejected() {
        User user = new User("Mohsina", "Tabassum", "m@example.com", "secret123");
        assertThrows(IllegalArgumentException.class,
                () -> user.setPassword(""));
    }
}
