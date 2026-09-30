-- ===========================================================================
--  DAY-06, STEP 2 — V6: every user gets a role.
--
--  WHY V6. V5 is the highest existing version (normalise_category_names), and
--  version numbers are a sequence, not a slot you pick. Note persona's copy of
--  this same migration is V3 — same change, different number, because the two
--  histories diverged at your wallets table. The number is a position in YOUR
--  history, not a label for the change.
--
--  WHAT THIS BUYS. Day-05 answered "are you who you say you are" (BCrypt).
--  Authentication. This column is the start of the other half — AUTHORIZATION,
--  "you are who you say you are, and you still may not do that". By step 6,
--  GET /users will require ADMIN, and a logged-in normal user hitting it gets
--  403, not 401. The difference between those two numbers is the whole day.
--
--  ⚠️ THIS IS THE STEP WITH THE WIDEST BLAST RADIUS IN PAYNEST, and not for a
--  reason you would guess from its size. You have FOUR UserRepository
--  implementations — in-memory, JDBC, JPA and Spring Data — where persona had
--  two. Adding a column to the table is three lines; making all four agree
--  about it is step 4, and it is the biggest step of the day. Nothing to do
--  about that here, but write this migration knowing what it commits you to.
-- ===========================================================================


-- ---------------------------------------------------------------------------
--  2a. Add the column.
--
--  Three decisions, each one load-bearing:
--
--  NOT NULL. A null role is a user whose permissions are undefined, and code
--  reading that column would need a null branch on every single check — the
--  kind of branch that gets written as "if null, allow" by someone in a hurry.
--  There is no such thing as a user with no role.
--
--  DEFAULT 'USER'. You have existing rows. NOT NULL with no default cannot be
--  added to a populated table — Postgres would have to invent a value and
--  refuses to. More importantly the default has to be the LEAST privileged
--  value: this migration silently assigns a role to every account that already
--  exists, and the safe direction for a silent assignment is downward. A
--  DEFAULT 'ADMIN' would hand every existing row full privileges, and it would
--  work perfectly and never error.
--
--  VARCHAR(20), not an enum type and not a separate roles table. Both of those
--  are better at scale, and for two values today they are ceremony. Note the
--  honest cost: you are choosing this knowing a real app grows a roles table,
--  and the CHECK below is what keeps the cheap version from rotting.
--
--  Syntax:  ALTER TABLE <table> ADD COLUMN <name> <type> NOT NULL DEFAULT <v>;
--
--  Write it: table `users`, column `role`, VARCHAR(20), NOT NULL, default
--  'USER'.
-- ---------------------------------------------------------------------------


ALTER TABLE users
    ADD COLUMN role VARCHAR(20)
        NOT NULL DEFAULT 'USER';


-- ---------------------------------------------------------------------------
--  2b. Constrain what may go in it.
--
--  Without this, `role` is a free-text field and every one of these inserts
--  succeeds:
--
--      'admin'      -- lowercase. hasRole('ADMIN') will not match it.
--      'ADMIN '     -- trailing space. Also will not match.
--      'SUPERUSER'  -- a role no code in this project has ever heard of.
--      ''           -- NOT NULL is satisfied. Empty string is not null.
--
--  ⚠️ Every one of those fails CLOSED — the user just quietly has no
--  privileges, no error anywhere. Which sounds like the safe direction until
--  you picture debugging it: the row looks right in psql, the code looks right,
--  and `hasRole('ADMIN')` returns false for reasons invisible to both.
--
--  This is the identical move you made in V1 with UNIQUE on email, and your own
--  comment there says it best: "Never let the only copy of a correctness
--  guarantee live in application code." Java can validate the role on the way
--  in. Java also has four repository implementations and a Flyway-managed
--  schema that psql can write to directly.
--
--  Syntax:
--      ALTER TABLE <table> ADD CONSTRAINT <name> CHECK (<condition>);
--
--  Name it `users_role_valid` — a named constraint appears in the error message
--  when it fires, and `users_check1` tells the reader nothing. Condition:
--  role IN ('USER', 'ADMIN').
-- ---------------------------------------------------------------------------


ALTER TABLE users
    ADD CONSTRAINT users_role_valid
        CHECK (role IN ('USER', 'ADMIN'));


-- ---------------------------------------------------------------------------
--  2c. Document it in the database.
--
--  Same as V3's COMMENT ON COLUMN, and for the same audience: whoever has a
--  psql prompt at 3am and has never opened this repo.
--
--  Worth recording HERE rather than only in Java, because there is one fact
--  about this column that is invisible from SQL and will bite: Spring Security
--  stores authorities as opaque strings and `hasRole('ADMIN')` is DEFINED as
--  "has the authority ROLE_ADMIN" — it prepends the prefix itself. So this
--  column holds 'ADMIN' and the application must present it as 'ROLE_ADMIN'.
--  Store 'ROLE_ADMIN' here and hasRole() looks for ROLE_ROLE_ADMIN and
--  silently never matches. That mismatch is the single most common Spring
--  Security bug there is, it produces no error, and step 7 is where you handle
--  it.
--
--  Write a COMMENT ON COLUMN users.role saying what it holds, that it is
--  constrained to USER or ADMIN, and that the ROLE_ prefix is added by the
--  application rather than stored.
-- ---------------------------------------------------------------------------


COMMENT ON COLUMN users.role
        IS 'User authorization role: USER or ADMIN. Constrained to these two values. Spring Security prepends ROLE_ prefix at runtime; this column stores the base role name without the prefix.';


-- ===========================================================================
--  VERIFY — and verify more than "it ran".
--
--    1. Boot the app (or mvn flyway:migrate) and confirm V6 applied:
--         SELECT version, description, success FROM flyway_schema_history
--         ORDER BY installed_rank DESC LIMIT 3;
--
--    2. Confirm existing rows were backfilled, not left null:
--         SELECT role, count(*) FROM users GROUP BY role;
--       ⚠️ Check the count is NON-ZERO first. A GROUP BY over an empty table
--       returns no rows, and "no rows" looks like a pass. You have already
--       been bitten by an assertion that passed against empty data — a green
--       tick that proves nothing is worse than a red one.
--
--    3. PROVE THE CONSTRAINT ACTUALLY FIRES. This is the part people skip,
--       and an untested constraint is indistinguishable from a comment:
--         UPDATE users SET role = 'admin' WHERE id = (SELECT min(id) FROM users);
--       Expect: ERROR ... violates check constraint "users_role_valid".
--       If that UPDATE SUCCEEDS, 2b did not take effect. Then set one real
--       account to ADMIN — you will need it from step 6 onward:
--         UPDATE users SET role = 'ADMIN' WHERE email = '<your test email>';
--
--  NOTHING IN THE TEST SUITE WILL NOTICE ANY OF THIS. The suite is still red
--  from step 1, and even green it does not assert on schema constraints. psql
--  is the verification for this step, the same way curl will be for step 10.
-- ===========================================================================
