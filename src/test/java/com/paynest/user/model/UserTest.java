package com.paynest.user.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

class UserTest {

    @Test
    void emailIsWhatWePassedIn() {
        User user = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        assertEquals("khi0ne@example.com", user.getEmail());
    }

    @Test
    void firstNameCanBeChanged() {
        User user = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        user.setFirstName("lincoln");
        assertEquals("lincoln", user.getFirstName());
    }

    @Test
    void toStringDoesNotLeakThePassword() {
        User user = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        assertFalse(user.toString().contains("Pass1234#"));
    }

    @Test
    void blankEmailIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new User("Anishom", "Frost", "", "Pass1234#"));
    }

    @Test
    void blankPasswordIsRejected() {
        User user = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        assertThrows(IllegalArgumentException.class,
                () -> user.setPassword(""));
    }

    @Test
    void usernameFollowsAFirstNameChange() {
        User user = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        assertEquals("@anishom-frost", user.getUsername());
        user.setFirstName("lincoln");
        assertEquals("@lincoln-frost", user.getUsername());
    }

    @Test
    void nullImageIsAccepted() {
        User user = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        assertTrue(user.getImage().isEmpty());
    }

    @Test
    void blankImageIsRejected() {
        User user = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        assertThrows(IllegalArgumentException.class,
                () -> user.setImage(""));
    }

    @Test
    void twoUsersWithTheSameEmailAreTheSamePerson() {
        // Write this. Build two Users with the same email but different.
        // first names and passwords. Assert they are equal.
        User male = new User("Lincoln", "Frost", "khi0ne@example.com", "Pass1234#");
        User female = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        assertEquals(female, male);
    }

    @Test
    void equalUsersAreFoundInAHashSet() {
        Set<User> users = new HashSet<>();

        User anishom = new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
        users.add(anishom);

        User lincoln = new User("Lincoln", "Frost", "khi0ne@example.com", "Pass1234#");

        assertTrue(users.contains(lincoln));
        users.add(lincoln);
        assertEquals(1, users.size());
    }
}
