package com.paynest.user.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paynest.user.exception.UserNotFoundException;
import com.paynest.user.model.User;
import com.paynest.user.repository.InMemoryUserRepository;
import com.paynest.user.service.UserService;
import com.paynest.user.exception.DuplicateEmailException;

class UserServiceTest {

    private final UserService service = new UserService(new InMemoryUserRepository());

    private User anishom() {
        return new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
    }

    @Test
    void registeringStoresTheUser() {
        // count() == 1, findByEmail isPresent
        var user = anishom();
        service.register(user);

        assertEquals(1, service.count());
        assertTrue(service.findByEmail(user.getEmail()).isPresent());
    }

    @Test
    void secondSignupWithTheSameEmailIsRejected() {
        // assertThrows + count() still 1}
        service.register(anishom());

        DuplicateEmailException thrown = assertThrows(
                DuplicateEmailException.class,
                () -> service.register(anishom()));

        assertEquals("khi0ne@example.com", thrown.getEmail());
        assertEquals(1, service.count());
    }

    @Test
    void aDifferentEmailIsNotADuplicate() {
        // two users, count() == 2}
        service.register(anishom());
        service.register(new User("Lincoln", "Frost", "rauf00n@example.com", "Pass1234#"));

        assertEquals(2, service.count());
    }

    @Test
    void missingUserIsEmptyNotAnError() {
        // findByEmail(...).isEmpty()}
        assertTrue(service.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    void getByEmailThrowsWhenMissing() {
        // assertThrows}
        assertThrows(UserNotFoundException.class, () -> service.getByEmail("missing@example.com"));
    }

    @Test
    void deletingRemovesTheUser() {
        // count() == 0}
        service.register(anishom());
        service.deleteByEmail(anishom().getEmail());

        assertEquals(0, service.count());
    }

    @Test
    void deletingAMissingUserThrows() {
        // assertThrows}
        assertThrows(UserNotFoundException.class, () -> service.deleteByEmail("missing@example.com"));
        assertEquals(0, service.count());
    }
}


