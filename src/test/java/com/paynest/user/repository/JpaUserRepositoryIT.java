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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.exception.UserNotFoundException;
import com.paynest.user.model.User;

/*
 * =========================================================================
 * DAY-04 STEP 7b — the last step. Prove JpaUserRepository actually works.
 * =========================================================================
 *
 * Nothing has executed a single line of JpaUserRepository. It compiles, it
 * is profile-selected, and save() / flush() / refresh() / the catch block
 * have never run. This file is what changes that.
 *
 * It is the deliberate twin of JdbcUserRepositoryIT: same database, same
 * migrations, same questions. Two implementations proved interchangeable by
 * being asked the same things — which is what UserRepository has claimed
 * since Day-03 and what nothing has ever checked.
 *
 * -------------------------------------------------------------------------
 * THE MOST VALUABLE ASSERTION IN THIS FILE IS THE ONE YOU DO NOT WRITE.
 * -------------------------------------------------------------------------
 * With ddl-auto=validate in application-jpa-it.properties, this context
 * cannot START unless every @Column claim on User is true of the real table.
 * The method bodies test behaviour; THE ACT OF STARTING tests the mapping.
 *
 * -------------------------------------------------------------------------
 * 7b-i. THE CLASS DECLARATION — four annotations
 * -------------------------------------------------------------------------
 *   @SpringBootTest
 *   @ActiveProfiles({...})  TWO profiles, same lesson as step 6c:
 *                           one selects the BEAN, one loads the DATABASE.
 *                           Work out both names.
 *   @Transactional          rolls each test back. Note what that is: not
 *                           cleanup code, but the database's own atomicity
 *                           used as a fixture. It ALSO discards the
 *                           persistence context, so entities cannot leak
 *                           between tests — and it means everything here
 *                           runs inside ONE transaction, which is what makes
 *                           the first-level-cache test below possible.
 *
 * Your JdbcUserRepositoryIT has no @EnabledIfSystemProperty guard, so it
 * runs on every `mvn test` and needs paynest_test up. Match that here —
 * consistency between the two suites matters more than which convention.
 *
 */
@SpringBootTest
@ActiveProfiles({"jpa", "jpa-it"})
@Transactional
class JpaUserRepositoryIT {
    /*
     * -------------------------------------------------------------------------
     * 7b-ii. FIELDS
     * -------------------------------------------------------------------------
     *   @Autowired UserRepository repository;
     *       The INTERFACE, not JpaUserRepository. This test asks for "whatever
     *       implements UserRepository" and the profile decides. Nothing in the
     *       method bodies should name an implementation — the file would be
     *       nearly identical for any of the three.
     */
    @Autowired
    private UserRepository repository;
    /*
     *   @Autowired JdbcClient jdbc;
     *       Raw SQL alongside the ORM, on purpose. When the question is "what is
     *       ACTUALLY in the table?", asking Hibernate is asking the thing under
     *       test.
     */
    @Autowired
    private JdbcClient jdbc;
    /*
     *   @Autowired SpringDataUserRepository springData;
     *       Needed for an explicit flush() in the dirty-checking test.
     */
    @Autowired
    private SpringDataUserRepository springData;

    /*
     * A @BeforeEach that DELETEs from users, and a static helper building your
     * standard test user — copy both from JdbcUserRepositoryIT. Mind the
     * constructor order: yours is (firstName, lastName, email, password).
     */
    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM users").update();
    }

    private static User anishom() {
        return new User("Anishom", "Frost", "khi0ne@example.com", "Pass1234#");
    }

    /*
     * -------------------------------------------------------------------------
     * 7b-iii. THE FIVE TESTS THAT MIRROR THE JDBC SUITE
     * -------------------------------------------------------------------------
     * Port these from JdbcUserRepositoryIT, unchanged in intent:
     *
     *   1. savingAssignsAnIdAndTimestamp
     *      assertNull(getId()) FIRST, then save, then assertNotNull on id AND
     *      createdAt. The null-first assertion makes this a claim about the
     *      TRANSITION. It is also the only thing that proves your refresh() call
     *      works — createdAt is insertable=false, so without the refresh it
     *      would still be null here.
     */
    @Test
    void savingAssignsAnIdAndTimestamp() {
        User toSave = anishom();
        assertNull(toSave.getId(), "unsaved user must have no id");

        User saved = repository.save(toSave);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
    }

    /*
     *   2. aSavedUserCanBeFoundByEmail
     *      Assert the fields INDIVIDUALLY. User.equals compares email only, so
     *      assertEquals(user, found) would pass even if the mapping had swapped
     *      firstName and lastName — both are VARCHAR(100). The compiler checks
     *      types, the framework checks names, neither checks meaning.
     */
    @Test
    void aSavedUserCanBeFoundByEmail() {
        repository.save(anishom());

        User found = repository.findByEmail("khi0ne@example.com").orElseThrow();

        assertEquals("Anishom", found.getFirstName());
        assertEquals("Frost", found.getLastName());
        assertEquals("khi0ne@example.com", found.getEmail());
    }

    /*
     *   3. anUnknownEmailIsEmpty          — absence is not an error
     */
    @Test
    void anUnknownEmailIsEmpty() {
        assertTrue(repository.findByEmail("nobody@example.com").isEmpty());
    }

    /*
     *   4. deletingAMissingUserThrows     — proves the rows == 0 branch
     */
    @Test
    void deletingAMissingUserThrows() {
        assertThrows(UserNotFoundException.class,
                () -> repository.deleteByEmail("nobody@example.com"));
    }
    /*
     *   5. aDuplicateEmailIsRejected      — THE IMPORTANT ONE.
     *      This covers the code persona got WRONG on the first attempt: the
     *      catch block that identified the violation by querying the database,
     *      which auto-flushed a failed entity with a null id and buried the real
     *      error under Hibernate's own AssertionFailure.
     *
     *      Assert the exception type and that e.getEmail() carries the address.
     *      Do NOT add a count() assertion afterwards — the transaction is dead
     *      (25P02), so the query would be refused. The constraint is what
     *      guarantees the second row did not land; the exception is the proof it
     *      fired. Write that reasoning as a comment.
     */
    @Test
    void aDuplicateEmailIsRejected() {
        repository.save(anishom());

        DuplicateEmailException e = assertThrows(DuplicateEmailException.class,
                () -> repository.save(anishom()));

        assertEquals("khi0ne@example.com", e.getEmail());
    }

    /*
     * -------------------------------------------------------------------------
     * 7b-iv. TWO TESTS THAT ONLY MAKE SENSE FOR JPA
     * -------------------------------------------------------------------------
     * These have no JDBC equivalent. They demonstrate the mechanism rather than
     * the behaviour, and they are the reason this file is worth more than a copy.
     *
     *   6. findByEmailTwiceReturnsTheSameObject
     *      Save, then call findByEmail TWICE. Assert with assertSame — not
     *      assertEquals. The second lookup never touches the database: inside a
     *      transaction Hibernate keeps a map of every entity it has handed out,
     *      so one row is one OBJECT, guaranteed by reference identity.
     *
     *      This is the persistence context, made visible. It is also why equals
     *      on email still matters: ACROSS transactions these would be different
     *      objects and only equals could tell they are the same person.
     */
    @Test
    void findByEmailTwiceReturnsTheSameObject() {
        repository.save(anishom());

        User first = repository.findByEmail("khi0ne@example.com").orElseThrow();
        User second = repository.findByEmail("khi0ne@example.com").orElseThrow();

        assertSame(first, second, "one row is one object inside a transaction");
    }

    /*
     *   7. mutatingAManagedEntityWritesWithoutSave
     *      Save, load it back, call setFirstName(...) — and call NO save().
     *      Then springData.flush() to force the write inside the test (otherwise
     *      it happens at commit, which this test rolls back instead).
     *
     *      Now assert with RAW SQL via jdbc, reading first_name straight from
     *      the table. Asking Hibernate would just return the object already in
     *      the persistence context and prove nothing.
     *
     *      The uncomfortable half of the lesson: inside a transaction a managed
     *      entity IS the row. A stray setter anywhere — a defensive
     *      normalisation, a fixup in a helper — persists silently. "I only
     *      changed it in memory" is not a thing that exists here.
     *
     * -------------------------------------------------------------------------
     */
    @Test
    void mutatingAManagedEntityWritesWithoutSave() {
        repository.save(anishom());
        User managed = repository.findByEmail("khi0ne@example.com").orElseThrow();

        managed.setFirstName("Turhan");
        springData.flush();

        String stored = jdbc.sql("SELECT first_name FROM users WHERE email = ?")
                .param("khi0ne@example.com")
                .query(String.class)
                .single();

        assertEquals("Turhan", stored, "no save() was called, and the row changed");
    }
}
/*
 * THEN:  ./mvnw clean test
 *
 * Expect 38 tests (31 + 7). And watch for TWO things in the log:
 *   - the open-in-view WARN should be GONE for this suite
 *   - a Schema-validation failure naming a column is a SUCCESS, not a
 *     setback: it means validate finally audited your step-3 mapping and
 *     found something nothing else would have caught.
 * =========================================================================
 */
