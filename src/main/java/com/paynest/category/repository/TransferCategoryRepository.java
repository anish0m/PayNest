package com.paynest.category.repository;

import com.paynest.category.model.Category;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The N:N between transfers and categories, read and written through the link
 * table directly.
 *
 * <p><b>JdbcClient rather than JPA, deliberately.</b> {@code transfer_categories}
 * has no entity class and should not have one: no identity of its own (composite
 * PK, no surrogate id), no behaviour, and nothing ever loads "a link row" as an
 * object — callers want the CATEGORIES for a transfer, or the TRANSFERS in a
 * category. Mapping it as an entity would create a class that exists only to be
 * immediately joined away.
 *
 * <p>First place in PayNest where JDBC is used <em>alongside</em> JPA rather than
 * instead of it. JPA for things with identity and lifecycle (User, Category);
 * JDBC for set operations, joins and link tables.
 *
 * <p>No {@code @Profile} here. The three {@code UserRepository} implementations
 * carry test/jdbc/jpa because they are three answers to one question and Spring
 * must pick one. This class is the only answer to its question.
 */
@Repository
public class TransferCategoryRepository {

    private final JdbcClient jdbc;

    public TransferCategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tags a transfer with a category. Idempotent.
     *
     * <p>{@code ON CONFLICT DO NOTHING} makes "make sure this transfer is tagged
     * food" the same operation whether or not it already was, so retries are
     * free. The composite PRIMARY KEY from V4 is what detects the conflict — no
     * application check, and therefore no race.
     *
     * @return true if this call created the link, false if it already existed
     */
    public boolean tag(UUID transferId, Long categoryId) {
        int rows = jdbc.sql("""
                        INSERT INTO transfer_categories (transfer_id, category_id)
                        VALUES (?, ?)
                        ON CONFLICT DO NOTHING
                        """)
                .params(transferId, categoryId)
                .update();

        return rows > 0;
    }

    /**
     * Removes a tag.
     *
     * <p>The row count is checked because a DELETE matching nothing is a
     * perfectly successful statement in SQL, and silence would look like
     * success. Whether "untag something that was not tagged" is a 404 or a 204
     * is the controller's decision, not this class's.
     */
    public boolean untag(UUID transferId, Long categoryId) {
        int rows = jdbc.sql("""
                        DELETE FROM transfer_categories
                         WHERE transfer_id = ? AND category_id = ?
                        """)
                .params(transferId, categoryId)
                .update();

        return rows > 0;
    }

    /**
     * Builds a Category from a result row.
     *
     * <p>{@code created_at} is read as {@link java.time.OffsetDateTime} and then
     * converted — pgjdbc rejects {@code getObject(col, Instant.class)} for
     * {@code timestamptz} because an Instant has no offset to invent. Same shape
     * as {@code JdbcUserRepository.mapRow}.
     */
    private static Category mapCategory(ResultSet rs, int rowNum) throws SQLException {
        return Category.fromRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant());
    }

    /**
     * One transfer, many categories — the first half of the N:N.
     *
     * <p>Uses the composite PK's index (transfer_id first), so this is a B-tree
     * lookup rather than a scan. {@code ORDER BY} is not decoration: without it
     * SQL guarantees nothing about row order, and the order can change when the
     * planner picks a different access path.
     */
    public List<Category> findCategoriesForTransfer(UUID transferId) {
        return jdbc.sql("""
                        SELECT c.id, c.name, c.created_at
                          FROM transfer_categories tc
                          JOIN categories c ON c.id = tc.category_id
                         WHERE tc.transfer_id = ?
                         ORDER BY c.name
                        """)
                .param(transferId)
                .query(TransferCategoryRepository::mapCategory)
                .list();
    }

    /**
     * One category, many transfers — the other half.
     *
     * <p><b>This is the query that needs {@code idx_transfer_categories_category_id}
     * from V4.</b> The composite PK is (transfer_id, category_id), left to right,
     * so it cannot serve a lookup by category_id alone — any more than a phone
     * book sorted by surname can find every "Anishom".
     *
     * <p>Returns UUIDs rather than Transaction objects because there is no
     * Transaction entity until Day-08. This method answers "which transfers";
     * resolving those to rows is a separate job.
     */
    public List<UUID> findTransfersForCategory(Long categoryId) {
        return jdbc.sql("""
                        SELECT tc.transfer_id
                          FROM transfer_categories tc
                         WHERE tc.category_id = ?
                         ORDER BY tc.created_at DESC
                        """)
                .param(categoryId)
                .query(UUID.class)
                .list();
    }

    /**
     * How many transfers carry a given category.
     *
     * <p>{@code COUNT(*)} is computed by the database; the rows never leave it.
     * Used by {@code delete()} to report how many transfers are blocking a
     * category's removal, without loading every UUID to call {@code size()}.
     */
    public long countTransfersForCategory(Long categoryId) {
        return jdbc.sql("SELECT COUNT(*) FROM transfer_categories WHERE category_id = ?")
                .param(categoryId)
                .query(Long.class)
                .single();
    }

    /**
     * Per-category totals — a taste of Day-16's report service, and the clearest
     * proof the N:N is real: this answer can only be produced by aggregating
     * across the link table.
     */
    public List<Map<String, Object>> countByCategory() {
        return jdbc.sql("""
                        SELECT c.name, COUNT(*) AS total
                          FROM transfer_categories tc
                          JOIN categories c ON c.id = tc.category_id
                         GROUP BY c.name
                         ORDER BY total DESC
                        """)
                .query()
                .listOfRows();
    }
}
