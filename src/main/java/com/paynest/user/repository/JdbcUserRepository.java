package com.paynest.user.repository;

import com.paynest.user.exception.DuplicateEmailException;
import com.paynest.user.exception.UserNotFoundException;
import com.paynest.user.model.User;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Optional;

/**
 * Stores users in PostgreSQL.
 *
 * <p>The second implementation of {@link UserRepository}, and the one that makes
 * the interface worth having. {@code UserService} was changed to depend on the
 * interface rather than the concrete in-memory class; nothing above this package
 * needed another edit to reach a real database.
 *
 * <p>No {@code @Profile} here — this is the default. {@code InMemoryUserRepository}
 * carries {@code @Profile("test")}, so exactly one of the two is ever eligible.
 * If both were, Spring would fail at <em>startup</em> with
 * {@code NoUniqueBeanDefinitionException} — not at compile time.
 *
 * <pre>
 * =========================================================================
 * DAY-04 STEP 6a — THE PARAGRAPH ABOVE IS NOW OUT OF DATE, AND IT PREDICTED
 * ITS OWN FAILURE. Read it again: "if both were, Spring would fail at
 * startup with NoUniqueBeanDefinitionException". There are now THREE
 * implementations, and this one is eligible ALWAYS.
 *
 * "The default" was a fine way to say "the real one" while there was
 * exactly one real one. It is not a claim that survives a third arrival —
 * outside the test profile, this bean and JpaUserRepository BOTH match, and
 * the application will refuse to start.
 *
 * Compare Day-03's mirror image: a MISSING `implements UserRepository`
 * compiled fine and produced NoSuchBeanDefinitionException at startup. Too
 * few and too many fail the same way, at the same moment. That is the
 * container earning its keep.
 *
 * WHAT TO DO:
 *   - add @Profile("jdbc") to this class, so it names itself POSITIVELY
 *     instead of relying on being the leftover
 *   - fix the paragraph above: it should say all three now name themselves,
 *     and that being "the default" is what broke
 *
 * THE TRANSFERABLE POINT: an implicit default, like a negation, encodes an
 * assumption about how many alternatives will ever exist. Both are wrong the
 * first time a new one shows up.
 * =========================================================================
 * </pre>
 *
 * <p>{@code @Repository} is not decoration. Besides registering the bean, it
 * switches on Spring's exception translation, which converts Postgres's
 * {@code PSQLException} (SQLSTATE 23505) into the vendor-neutral
 * {@link DuplicateKeyException} that {@link #save} catches. Without the
 * annotation that translation does not happen and the catch never fires.
 *
 * <p>No JPA yet, deliberately. An ORM that generates SQL you have never written
 * is a tool you cannot debug. Today the SQL is visible and boring on purpose;
 * Day-04 replaces it.
 */
@Repository
@Profile("jdbc")
public class JdbcUserRepository implements UserRepository {

    /**
     * Named once so a column rename is one edit rather than five.
     *
     * <p>Explicit columns, not {@code SELECT *}: with a star, adding a column to
     * the table silently changes what every query returns, and the column order
     * in the table becomes load-bearing in Java.
     */
    private static final String COLUMNS =
            "id, first_name, last_name, email, password_hash, image, created_at";

    private final JdbcClient jdbc;

    public JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a user and returns it with the id and timestamp the database assigned.
     *
     * <p><b>RETURNING is doing real work.</b> It is a Postgres extension that makes
     * an INSERT hand back columns from the row it just wrote, so one round trip both
     * writes the row and reports the generated values. The alternative — INSERT then
     * SELECT — is two round trips, and the SELECT needs a way to identify the row it
     * just created, which is awkward precisely because the identifier is the thing
     * being fetched.
     *
     * <p>This is why {@code save} returns a {@link User} rather than {@code void},
     * and why {@code id} and {@code createdAt} are constructor-only: the returned
     * object is the only place those values exist.
     *
     * <p><b>The duplicate check is a catch, not an if.</b> {@code if (exists) throw}
     * is a look-then-act: two concurrent signups both look, both find nothing, both
     * insert. The UNIQUE constraint has no such gap. Application code races;
     * constraints do not. So the insert is attempted and the refusal is translated.
     *
     * <p>Note which columns are absent from the INSERT: {@code id} and
     * {@code created_at}. The database generates both — V1's schema decision showing
     * up in Java.
     */
    @Override
    public User save(User user) {
        try {
            return jdbc.sql("""
                            INSERT INTO users (first_name, last_name, email, password_hash, image)
                            VALUES (?, ?, ?, ?, ?)
                            RETURNING
                            """ + COLUMNS)
                    .params(user.getFirstName(), user.getLastName(), user.getEmail(),
                            user.getPasswordHash(), user.getImage().orElse(null))
                    .query(JdbcUserRepository::mapRow)
                    .single();
        } catch (DuplicateKeyException e) {
            throw new DuplicateEmailException(user.getEmail());
        }
    }

    /**
     * Finds a user, or empty if there is none.
     *
     * <p><b>The {@code ?} is not string formatting.</b> Written as
     * {@code "... WHERE email = '" + email + "'"}, an email of
     * {@code x'; DROP TABLE users; --} is executed as SQL. With a placeholder the
     * value is sent separately from the statement, so the database parses the query
     * first and the value can never be read as code. This is not escaping — it is a
     * different channel.
     *
     * <p>{@code optional()} returns empty for no rows and throws if more than one
     * comes back. That second behaviour is worth having: two rows for one email
     * would mean the UNIQUE constraint was not doing its job, and silently taking
     * the first would hide it.
     */
    @Override
    public Optional<User> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE email = ?")
                .param(email)
                .query(JdbcUserRepository::mapRow)
                .optional();
    }

    /** Finds a user, or throws because the caller cannot continue without one. */
    @Override
    public User getByEmail(String email) {
        return findByEmail(email).orElseThrow(() -> new UserNotFoundException(email));
    }

    /**
     * <p>{@code update()} returns the number of rows affected, so one statement both
     * deletes and reports whether anything was there — no SELECT first, and therefore
     * no gap between checking and acting. Same reasoning as {@link #save} catching
     * rather than asking.
     */
    @Override
    public void deleteByEmail(String email) {
        int rows = jdbc.sql("DELETE FROM users WHERE email = ?")
                .param(email)
                .update();
        if (rows == 0) {
            throw new UserNotFoundException(email);
        }
    }

    /**
     * <p>ORDER BY is not decoration. Without it SQL makes <b>no guarantee whatsoever</b>
     * about row order — it may match insertion order for months and then change when
     * the planner picks a different access path, a bug that appears in production and
     * cannot be reproduced locally.
     *
     * <p>This method also has a real flaw: it loads every user into memory. Fine at a
     * hundred, fatal at a million, and the failure arrives gradually. Day-07 replaces
     * it with pagination — and the stable order established here is a precondition for
     * that, because paging through an unordered result can show the same row twice and
     * skip another.
     */
    @Override
    public Collection<User> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users ORDER BY id")
                .query(JdbcUserRepository::mapRow)
                .list();
    }

    /**
     * <p>{@code COUNT(*)} is computed by the database; the row data never leaves it.
     * Counting in Java would mean transferring every row across the network to call
     * {@code size()}. Send the question to the data, not the data to the question.
     */
    @Override
    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM users")
                .query(Long.class)
                .single();
    }

    /**
     * One row of the result set, as a {@link User}.
     *
     * <p>This method is the entire object-relational mapping layer, written by hand.
     * The column names are {@code snake_case} and the Java is {@code camelCase};
     * nothing bridges that automatically — this method is the bridge. That mismatch
     * is one of the things JPA handles by convention, and one of the things that makes
     * JPA feel like magic until you have seen the mapping it replaces.
     *
     * <p><b>Argument order is load-bearing and unchecked.</b> Five of these seven
     * parameters are {@code String}. Passing {@code email} where {@code firstName}
     * belongs compiles green and corrupts every row it reads. The compiler checks
     * types, not meanings.
     *
     * <p><b>Reading the timestamp.</b> The obvious
     * {@code getObject("created_at", Instant.class)} is rejected by the driver:
     * <em>conversion to class java.time.Instant from timestamptz not supported</em>.
     * An {@link java.time.Instant} is a point on the timeline with no offset, so the
     * driver will not perform a conversion that has to invent one.
     * {@link java.time.OffsetDateTime} is what a {@code timestamptz} genuinely is, and
     * {@code toInstant()} then discards the offset explicitly — the conversion happens
     * here, where it is visible, rather than silently inside a driver.
     *
     * <p>Deliberately not {@code getTimestamp()}, which returns a legacy
     * {@code java.sql.Timestamp} carrying the JVM's default timezone and can therefore
     * shift a stored UTC instant by hours depending on where the server runs. Avoiding
     * exactly that was the point of TIMESTAMPTZ.
     */
    private static User mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                rs.getString("password_hash"),
                rs.getString("image"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant());
    }
}
