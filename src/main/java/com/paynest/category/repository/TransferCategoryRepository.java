package com.paynest.category.repository;

import com.paynest.category.model.Category;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// =========================================================================

//  DAY-05, STEP 2.5c(ii) — THE N:N ITSELF. This is the file that matters.
// =========================================================================
//  Everything else in step 2.5 is CRUD you have written three times. This
//  class is the new thing: reading and writing a many-to-many WITHOUT an
//  @ManyToMany annotation, using the link table directly.
//
//  Imports: JdbcClient, @Repository, java.util.{List, UUID},
//           com.paynest.category.model.Category
//
//  ⚠️ WHY JdbcClient AND NOT JPA HERE.
//  `transfer_categories` has no entity class and should not have one. It
//  has no identity of its own (composite PK, no surrogate id), no
//  behaviour, and nothing ever loads "a link row" as an object — you
//  always want the CATEGORIES for a transfer, or the TRANSFERS in a
//  category. Mapping it as an entity would create a class that exists only
//  to be immediately joined away.
//
//  This is the first place in PayNest where you deliberately use JDBC
//  *alongside* JPA rather than instead of it. Both are on the classpath,
//  and "which tool for which job" is a real answer now:
//
//      JPA    — things with identity and lifecycle (User, Category)
//      JDBC   — set operations, joins, link tables, reporting queries
//
//  Day-16's report service is made of the second kind.
//
//  ---------------------------------------------------------------
//  CLASS DECLARATION:
//
//      @Repository
//      public class TransferCategoryRepository { ... }
//
//  ⚠️ NO @Profile HERE, and this is worth a sentence.
//  Your three UserRepository implementations carry test/jdbc/jpa because
//  they are THREE ANSWERS TO ONE QUESTION and Spring must pick one. This
//  class is the only answer to its question, so there is nothing to
//  select between. A profile here would just mean "sometimes this feature
//  does not exist."
//
//  Constructor-inject a JdbcClient, final field, no @Autowired.
// =========================================================================
@Repository
public class TransferCategoryRepository {

    private final JdbcClient jdbc;

    // -------------------------------------------------------------------------
//  1. tag(UUID transferId, Long categoryId)  ->  boolean (or void)
//
//     INSERT INTO transfer_categories (transfer_id, category_id)
//     VALUES (?, ?)
//     ON CONFLICT DO NOTHING
//
//     ⚠️ ON CONFLICT DO NOTHING is the interesting clause. Without it,
//     tagging the same transfer twice throws DuplicateKeyException and you
//     must catch it. With it, the database absorbs the repeat silently and
//     .update() returns 0 instead of 1.
//
//     Which one you want is a genuine design question, so DECIDE AND SAY
//     WHY in a comment:
//
//       - ON CONFLICT DO NOTHING  -> tagging is IDEMPOTENT. "Make sure
//         this transfer is tagged food" is the same operation whether or
//         not it already was. Retries are free. This is the Day-11 idea
//         (idempotency) arriving early, and it is usually right for a
//         tag.
//
//       - let it throw -> the caller learns the tag already existed. Only
//         useful if "already tagged" is an error a human should see, and
//         for tagging it almost never is.
//
//     RECOMMENDATION: ON CONFLICT DO NOTHING, return the row count so the
//     caller CAN distinguish "added" (1) from "already there" (0) if it
//     wants, without being forced to.
//
//     Note what you get for free: the composite PRIMARY KEY from V4 is
//     what ON CONFLICT detects. No application check, no race. Policy vs
//     guarantee, again.
// -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
//  2. untag(UUID transferId, Long categoryId)  ->  boolean
//
//     DELETE FROM transfer_categories
//      WHERE transfer_id = ? AND category_id = ?
//
//     Return `rows > 0` so the caller knows whether anything was removed.
//     Same reasoning as JdbcUserRepository.deleteByEmail checking the row
//     count: a DELETE matching nothing is a perfectly successful statement
//     in SQL, and silence would look like success.
//
//     Whether "untag something that was not tagged" is a 404 or a 204 is
//     the controller's decision, not this class's. Report the fact; let
//     the edge decide what it means.
// -------------------------------------------------------------------------
    public boolean untag(UUID transferId, Long categoryId) {
        int rows = jdbc.sql("""
                        DELETE FROM transfer_categories
                         WHERE transfer_id = ? AND category_id = ?
                        """)
                .params(transferId, categoryId)
                .update();

        return rows > 0;
    }

    // -------------------------------------------------------------------------
//  3. findCategoriesForTransfer(UUID transferId)  ->  List<Category>
//
//     THE FIRST HALF OF THE N:N. A JOIN across the link table:
//
//     SELECT c.id, c.name, c.created_at
//       FROM transfer_categories tc
//       JOIN categories c ON c.id = tc.category_id
//      WHERE tc.transfer_id = ?
//      ORDER BY c.name
//
//     ⚠️ ORDER BY is not decoration — your V2 comment already made this
//     point. Without it SQL guarantees NOTHING about row order, and the
//     order can change when the planner picks a different access path.
//
//     This query uses the composite PK's index (transfer_id first), so it
//     is a B-tree lookup, not a scan.
//
//     You will need a mapRow-style method to build a Category from the
//     ResultSet. ⚠️ You have no public constructor taking (id, name,
//     createdAt) — only Category(String name). Two honest options:
//
//       a) add a package-private or reconstruction constructor to
//          Category, exactly as User has one for exactly this reason
//          (Day-03: "id can only be supplied where it is known"); or
//       b) return a small record instead of the entity.
//
//     (a) is more consistent with what you already did for User. Prefer it.
// -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
//  4. findTransfersForCategory(Long categoryId)  ->  List<UUID>
//
//     THE OTHER HALF — and the direction your niece example is actually
//     about: every monthly allowance.
//
//     SELECT tc.transfer_id
//       FROM transfer_categories tc
//      WHERE tc.category_id = ?
//      ORDER BY tc.created_at DESC
//
//     ⚠️ THIS IS THE QUERY THAT NEEDS THE EXTRA INDEX FROM V4 (4c).
//     The composite PK is (transfer_id, category_id) — left to right — so
//     it CANNOT serve a lookup by category_id alone, any more than a phone
//     book sorted by surname can find every "Anishom". Without
//     idx_transfer_categories_category_id this is a sequential scan.
//
//     WORTH PROVING RATHER THAN BELIEVING. Once there is data:
//
//         EXPLAIN SELECT tc.transfer_id FROM transfer_categories tc
//          WHERE tc.category_id = 1;
//
//     Look for "Index Scan" vs "Seq Scan". That one command is a better
//     demo than any amount of explaining, and it is the kind of thing that
//     makes a progress update land.
//
//     (On a table with five rows Postgres may choose Seq Scan anyway —
//     scanning five rows is cheaper than reading an index. That is the
//     planner being right, not the index being useless. Mention it before
//     someone else does.)
//
//     Returns UUIDs, not Transaction objects, because there is no
//     Transaction entity until Day-08. That is honest: this method answers
//     "which transfers", and resolving those to rows is a separate job.
// -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
//  5. countByCategory()  ->  a summary, optional but worth having
//
//     SELECT c.name, COUNT(*) AS total
//       FROM transfer_categories tc
//       JOIN categories c ON c.id = tc.category_id
//      GROUP BY c.name
//      ORDER BY total DESC
//
//     One row per category. This is a taste of Day-16's report service,
//     and it is the single clearest demonstration that the N:N is real:
//     it can only be produced by aggregating ACROSS the link table.
//
//     Return List<Map<String,Object>> or a small record — your choice.
//     COUNT(*) is computed by the database; the rows never leave it.
// -------------------------------------------------------------------------
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
