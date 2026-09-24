-- ===========================================================================
--  DAY-05, STEP 2.5 — FIX 1: V5, normalise the seeded category names.
--
--  🔴 THE BUG THIS FIXES, and it is the one that would have broken your
--  demo in front of your mentor.
--
--  V4 seeded capitalised names:
--
--      INSERT INTO categories (name) VALUES ('Allowance'), ('Rent'), ...
--
--  Category.setName() normalises with trim().toLowerCase(Locale.ROOT).
--  That decision was RIGHT — "Food", "food" and " food " are one category
--  to a human and three rows to a UNIQUE constraint.
--
--  But the seed data does not follow the rule the application enforces:
--
--      GET /categories/allowance   -> 404   (the row says "Allowance")
--      GET /categories/Allowance   -> 404   (findByName is exact-match SQL)
--
--  ⚠️ ALL 16 SEEDED CATEGORIES ARE UNREACHABLE BY NAME. Which means tag()
--  can never use one, because it calls getByName first.
--
--  THE SHAPE OF THE MISTAKE, worth more than the fix:
--  the rule lived in ONE place (the setter) and the data entered through
--  ANOTHER (a migration). Normalisation applied on the way in through Java
--  is not applied to rows that never went through Java. Anything that
--  writes to the table without passing through your model bypasses every
--  invariant the model has.
--
--  This is the same family as Day-03's "look-then-act" gap: an application
--  rule is not a database rule. The difference here is that nothing races
--  — the data was simply born wrong.
--
--  THE STRONGER FIX, worth knowing and NOT doing today:
--  a CHECK constraint would make the database enforce it too —
--      ALTER TABLE categories ADD CONSTRAINT categories_name_lowercase
--          CHECK (name = lower(name));
--  Then a capitalised INSERT from ANY source is refused, and this class of
--  bug becomes unrepresentable rather than merely fixed. Consider adding
--  it; if you do, add it in this file and say so.
-- ===========================================================================


-- ---------------------------------------------------------------------------
--  5a. Normalise the existing rows.
--
--  UPDATE categories SET name = lower(trim(name));
--
--  Write that below. It is safe: UNIQUE still holds, because none of the
--  16 seeded names collide once lowercased ("Rent" and "rent" would, but
--  you only ever inserted one of each).
--
--  ⚠️ IF THEY DID COLLIDE, this UPDATE would fail with a unique violation —
--  and that would be the database refusing to silently merge two distinct
--  categories into one. Once again it checks rather than assumes.
-- ---------------------------------------------------------------------------

UPDATE categories
SET name = lower(trim(name));

-- ---------------------------------------------------------------------------
--  5b. OPTIONAL, and recommended — make it enforceable.
--
--  Add the CHECK constraint described above so the database refuses a
--  non-lowercase name from any source, not just from Java.
--
--  Decide, and write one line saying which you chose and why. If you skip
--  it, the reason "the application normalises, and V5 fixed the only other
--  writer" is defensible — just say it out loud rather than leaving the
--  question open.
-- ---------------------------------------------------------------------------

ALTER TABLE categories
    ADD CONSTRAINT categories_name_lowercase
        CHECK (name = lower(trim(name)));

-- ---------------------------------------------------------------------------
--  5c. Verify, after ./mvnw clean test runs this:
--
--      SELECT name FROM categories ORDER BY name;
--          -> all lowercase
--
--      curl localhost:8090/categories/allowance
--          -> 200, not 404
-- ---------------------------------------------------------------------------
