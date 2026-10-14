package com.paynest.user.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.paynest.user.dto.CreateUserRequest;
import com.paynest.user.exception.UserNotFoundException;
import com.paynest.user.repository.InMemoryUserRepository;
import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.model.User;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.service.WalletService;

class UserServiceTest {

    // A real encoder, not a mock — a mock returning "hashed-" + raw would pass
    // while proving nothing about BCrypt. Costs ~50-100ms per register() call;
    // that is the dated debt recorded in SecurityConfig, paid deliberately
    // rather than testing a configuration that does not ship.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final RecordingWalletService wallets = new RecordingWalletService();
    private final UserService service =
            new UserService(new InMemoryUserRepository(), encoder, wallets);

    private CreateUserRequest anishom() {
        return new CreateUserRequest(
                "Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
    }

    @Test
    void registeringStoresTheUser() {
        var user = anishom();
        service.register(user);

        var savedUser = service.getByEmail(user.email());

        // matches(), never equals() — encode() draws a fresh random salt
        // every call, so encode(x).equals(encode(x)) is false, always.
        assertTrue(encoder.matches(user.password(), savedUser.getPasswordHash()));
        assertNotEquals(user.password(), savedUser.getPasswordHash());

        assertEquals(1, service.count());
        assertTrue(service.findByEmail(user.email()).isPresent());
    }

    @Test
    void registeringHashesThePassword() {
        var request = anishom();

        service.register(request);

        var savedUser = service.getByEmail(request.email());

        assertNotEquals(request.password(), savedUser.getPasswordHash());
        assertTrue(encoder.matches(request.password(), savedUser.getPasswordHash()));
    }

    @Test
    void twoUsersWithTheSamePasswordGetDifferentHashes() {
        var first = anishom();
        var second = new CreateUserRequest(
                "Lincoln", "Frost", "rauf00n@example.com", first.password());

        service.register(first);
        service.register(second);

        var firstSaved = service.getByEmail(first.email());
        var secondSaved = service.getByEmail(second.email());

        assertNotEquals(firstSaved.getPasswordHash(), secondSaved.getPasswordHash());
        assertTrue(encoder.matches(first.password(), firstSaved.getPasswordHash()));
        assertTrue(encoder.matches(second.password(), secondSaved.getPasswordHash()));
    }

    @Test
    void secondSignupWithTheSameEmailIsRejected() {
        service.register(anishom());

        DuplicateEmailException thrown = assertThrows(
                DuplicateEmailException.class,
                () -> service.register(anishom()));

        assertEquals("khi0ne@example.com", thrown.getEmail());
        assertEquals(1, service.count());
    }

    @Test
    void aDifferentEmailIsNotADuplicate() {
        service.register(anishom());
        service.register(new CreateUserRequest("Lincoln", "Frost", "rauf00n@example.com", "Pass1234#"));

        assertEquals(2, service.count());
    }

    @Test
    void missingUserIsEmptyNotAnError() {
        assertTrue(service.findByEmail("missing@example.com").isEmpty());
    }

    @Test
    void getByEmailThrowsWhenMissing() {
        assertThrows(UserNotFoundException.class, () -> service.getByEmail("missing@example.com"));
    }

    @Test
    void deletingRemovesTheUser() {
        service.register(anishom());
        service.deleteByEmail(anishom().email());

        assertEquals(0, service.count());
    }

    @Test
    void deletingAMissingUserThrows() {
        assertThrows(UserNotFoundException.class, () -> service.deleteByEmail("missing@example.com"));
        assertEquals(0, service.count());
    }

    @Test
    void registeringAlsoCreatesAWallet() {
        service.register(anishom());

        assertEquals(1, wallets.createForCalls);
        assertEquals("khi0ne@example.com", wallets.lastUser.getEmail());
    }

    @Test
    void registerPropagatesWalletCreationFailure() {
        wallets.failOnCreate = true;

        assertThrows(IllegalStateException.class,
                () -> service.register(anishom()));
    }


    static class RecordingWalletService extends WalletService {
        int createForCalls = 0;
        boolean failOnCreate = false;

        User lastUser;

        RecordingWalletService() {
            // Safe because this fake overrides every method this test calls.
            super(null, null);
        }

        @Override
        public Wallet createFor(User user) {
            createForCalls++;
            lastUser = user;

            if (failOnCreate) {
                throw new IllegalStateException("wallet creation failed");
            }

            return new Wallet(user);
        }
    }
}
