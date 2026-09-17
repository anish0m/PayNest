package com.paynest.user.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.exception.UserNotFoundException;
import com.paynest.user.model.User;

/**
 * {@link JdbcUserRepository} against a real PostgreSQL database.
 *
 * <p><b>The first tests that execute any SQL.</b> The 26 unit tests are green and
 * none of them runs a line of this repository — they use the in-memory
 * implementation, or mock the service entirely. The column list, the RETURNING
 * clause, {@code mapRow}'s argument order and the timestamp conversion are
 * untested until this file runs.
 *
 * <p>Named {@code *IT}, not {@code *Test} — which is why the pom's surefire
 * configuration includes the {@code *IT.java} pattern. The name is the API, same
 * as {@code V1__} for Flyway and {@code application-jdbc-it} for Spring.
 *
 * <p>{@code @ActiveProfiles("jdbc-it")} is the switch. It loads
 * {@code application-jdbc-it.properties} and overrides the
 * {@code spring.profiles.active=test} in the test {@code application.properties},
 * so {@code InMemoryUserRepository} ({@code @Profile("test")}) is not eligible and
 * {@link JdbcUserRepository} is the only remaining candidate. Flyway then builds
 * the schema in {@code paynest_test} from the same migrations the application
 * uses — so a broken migration fails this suite.
 *
 * <p>{@code @Transactional} makes Spring open a transaction before each test and
 * roll it back afterwards. Note what that is: not cleanup code, but the database's
 * own atomicity guarantee used as a test fixture. (It also means these tests
 * cannot observe what a second connection would see, so this style cannot test
 * real concurrency — which matters from Day-13 onward.)
 */
@SpringBootTest
@ActiveProfiles("jdbc-it")
@Transactional
class JdbcUserRepositoryIT {

    /**
     * The field type is the interface, not the concrete class. This test asks for
     * "whatever implements {@link UserRepository}" and Spring supplies whichever
     * bean the active profile allows — so nothing here names an implementation.
     */
    @Autowired
    private UserRepository repository;

    @Autowired
    private JdbcClient jdbc;

    /** Belt and braces: the rollback already empties the table. */
    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM users").update();
    }

    private static User anishom() {
        return new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
    }

    /**
     * Proves RETURNING works: the database generates {@code id} and
     * {@code created_at}, and {@code save} hands them back.
     *
     * <p>Asserting null first makes this a claim about the <em>transition</em>
     * rather than about the returned object — it is {@code save} that assigns
     * these, and nothing else.
     */
    @Test
    void savingAssignsAnIdAndTimestamp() {
        User toSave = anishom();
        assertNull(toSave.getId(), "unsaved user must have no id");

        User saved = repository.save(toSave);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
    }

    /**
     * The round trip: written by {@code save}, read back by {@code findByEmail}.
     *
     * <p>The fields are asserted individually on purpose. {@code User.equals}
     * compares email only, so {@code assertEquals(user, found)} would pass even if
     * {@code mapRow} had swapped the first name and the email — five of its seven
     * arguments are {@code String}, and the compiler checks types, not meanings.
     */
    @Test
    void aSavedUserCanBeFoundByEmail() {
        repository.save(anishom());

        User found = repository.findByEmail("khi0ne@example.com").orElseThrow();

        assertEquals("Anishom", found.getFirstName());
        assertEquals("Frost", found.getLastName());
        assertEquals("khi0ne@example.com", found.getEmail());
    }

    /** Absence is not an error — the counterpart to {@code getByEmail} throwing. */
    @Test
    void anUnknownEmailIsEmpty() {
        assertTrue(repository.findByEmail("nobody@example.com").isEmpty());
    }

    /**
     * Two things at once: V1's UNIQUE constraint is genuinely enforced by Postgres,
     * and {@code save}'s catch block translates Spring's
     * {@code DuplicateKeyException} into this application's own exception — the same
     * one the in-memory implementation throws, which is what makes the two
     * interchangeable.
     *
     * <p><b>Why there is no {@code count()} assertion here.</b> An earlier version
     * added one and it failed with SQL state 25P02, <em>current transaction is
     * aborted</em>. In PostgreSQL a failed statement poisons the whole transaction:
     * every subsequent command is refused until rollback. Catching the exception in
     * Java does not undo it in the database — "handled" means two different things
     * on the two sides of that boundary. The constraint is what guarantees the
     * second row did not land, and the exception is the proof it fired.
     */
    @Test
    void aDuplicateEmailIsRejected() {
        repository.save(anishom());

        DuplicateEmailException e = assertThrows(DuplicateEmailException.class,
                () -> repository.save(anishom()));

        assertEquals("khi0ne@example.com", e.getEmail());
    }

    /**
     * Proves the {@code rows == 0} branch. Without it {@code deleteByEmail} would
     * succeed at deleting nothing, and silence is not a pass.
     */
    @Test
    void deletingAMissingUserThrows() {
        assertThrows(UserNotFoundException.class,
                () -> repository.deleteByEmail("nobody@example.com"));
    }
}
