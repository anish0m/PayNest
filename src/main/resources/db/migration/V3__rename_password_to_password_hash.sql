-- ===========================================================================
--  DAY-05, STEP 2 — V3: password becomes password_hash.
--
--  Your V1 comment wrote this migration's brief for you:
--
--      "Plaintext today, and that is a real debt with a due date: Day-05/06
--       introduces BCrypt and renames this to password_hash."
--
--  This is that due date. Note you are paying a debt you documented yourself,
--  in the place you documented it. That is what a dated comment is FOR — an
--  undated "TODO: fix this" would still be sitting there in Day-19.
--
--  WHY V3 AND NOT AN EDIT TO V1.
--  V1 has run. Its checksum is in flyway_schema_history. Editing it now makes
--  the checksum disagree and Flyway refuses to start rather than guess — which
--  feels hostile the first time and is exactly right, because the alternative
--  is a schema that silently differs between your laptop and production.
--  The schema's history is append-only for the same reason a ledger is.
--
--  WHY V3 AND NOT V2. Your V2 already exists (wallets and transactions). Version
--  numbers are a sequence, not a slot you pick — V2 is taken, so this is V3.
--  (persona's copy of this same migration is V2, because persona never had a
--  wallets table. Same change, different number, because the histories differ.)
--
--  WHY RENAME AT ALL, when password VARCHAR(255) would hold a hash perfectly
--  well? Two reasons, and the second is the one that matters:
--
--    1. A column named `password` containing a hash is a lie. The next person
--       to read this schema has to open the Java to find out which it is.
--    2. Renaming makes every line of code that assumed plaintext FAIL rather
--       than quietly keep working. You are about to see this happen in step 4:
--       the compiler will list your call sites for you instead of you trying
--       to remember them.
--
--       keep the name  ->  old code compiles, stores plaintext. Silent.
--       rename it      ->  old code breaks. Loud, and it breaks at build time.
--
--  Same instinct as GENERATED ALWAYS on your id and insertable=false on
--  created_at: make the wrong thing impossible to express, not merely
--  discouraged.
-- ===========================================================================


-- ---------------------------------------------------------------------------
--  2a. Rename the column.
--
--  PostgreSQL syntax:   ALTER TABLE <table> RENAME COLUMN <old> TO <new>;
--
--  Write that statement below. Table `users`, from `password` to
--  `password_hash`.
-- ---------------------------------------------------------------------------

ALTER TABLE users RENAME COLUMN password TO password_hash;

-- ---------------------------------------------------------------------------
--  2b. Narrow the column to VARCHAR(60).
--
--  A BCrypt hash is ALWAYS exactly 60 characters. Not "usually" — the format
--  fixes it:
--
--      $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
--      |   |  |                      |
--      |   |  +- salt, 22 chars      +- hash, 31 chars
--      |   +---- cost factor
--      +-------- algorithm version
--
--      4 + 22 + 31 = 57, plus three '$' separators = 60.
--
--  WHY NARROW IT, when VARCHAR(255) is harmless and already there?
--
--  Because a 255-wide column is wide enough to hold a plaintext password, and
--  a column that CAN hold the wrong thing eventually will. At 60, the day some
--  future code path forgets to hash, the INSERT fails loudly at the database
--  boundary instead of storing a readable password that nobody notices for a
--  year.
--
--  You are converting a check somebody has to remember into a constraint the
--  database enforces. That is the whole Day-05 theme in one ALTER statement.
--
--  PostgreSQL syntax:
--      ALTER TABLE <table> ALTER COLUMN <column> TYPE <type>;
--
--  Write it below: table `users`, column `password_hash`, type VARCHAR(60).
-- ---------------------------------------------------------------------------

ALTER TABLE users ALTER COLUMN password_hash TYPE VARCHAR(60);

-- ---------------------------------------------------------------------------
--  2c. Document it in the database itself.
--
--  Your V1 ends with a COMMENT ON TABLE. Same idea, on a column this time:
--  it is visible to anyone running \d+ or opening a GUI, including whoever is
--  holding a psql prompt at 3am having never read this repository.
--
--  Syntax:  COMMENT ON COLUMN users.password_hash IS '...';
--
--  Write one saying what this column holds — a BCrypt hash, 60 chars, salt and
--  cost included in the string, never a plaintext password.
-- ---------------------------------------------------------------------------

COMMENT
ON COLUMN users.password_hash IS
    'BCrypt hash, 60 chars, salt and cost factor included in the string. Never a plaintext password.';

-- ===========================================================================
--  WHAT THIS MIGRATION DELIBERATELY DOES NOT DO — worth reading, nothing to write.
--
--  It does not convert existing rows. Any plaintext password already in the
--  table stays exactly as it is, and will simply fail to match on login,
--  because a plaintext string is not a valid BCrypt hash.
--
--  That is the CORRECT outcome, and the reasoning is the part worth keeping:
--
--    - A plaintext password cannot be "upgraded" by hashing it in place,
--      because doing so requires READING it — which is the exact thing that
--      must stop being possible. It would also mean this migration file
--      handled every user's password in the clear.
--
--    - The honest treatment of a column that stored plaintext is that every
--      password in it is compromised. Real systems force a reset.
--
--  ⚠️ PRACTICAL CONSEQUENCE FOR YOU, TODAY:
--  step 2b will FAIL if any existing row has a password longer than 60 chars.
--  Your dev rows are short, so it will pass — but notice WHY it passes: the
--  database checked, it did not assume. Postgres is being more careful than
--  the application, again.
-- ===========================================================================
