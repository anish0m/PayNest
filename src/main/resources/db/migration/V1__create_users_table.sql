-- ===========================================================================
-- V1__create_users_table.sql
--
-- THE FILENAME IS AN API. Flyway reads it, not a config file:
--     V    versioned migration (runs once, in order)
--     1    the version number
--     __   TWO underscores. One and Flyway does not recognise the file at all.
--
-- AND IT IS IMMUTABLE. Once this has run anywhere, Flyway stores a checksum of
-- its contents in flyway_schema_history and refuses to start if the file
-- changes. A schema change on Day-04 is a NEW file, V2 — never an edit here.
-- That rule is the entire value: it guarantees every machine, and production,
-- applied an identical sequence of changes.
--
-- WHY THIS FILE EXISTS AT ALL. The alternative is a person typing CREATE TABLE
-- into a terminal once, which means the schema lives only in the database and
-- in that person's memory. Here the schema is code: in git, reviewed, and the
-- same everywhere.
-- ===========================================================================

CREATE TABLE users (

    -- A SURROGATE key: it means nothing outside this database. That is the
    -- point. Email is the natural key and would "work", but:
    --   1. people change their email, and once wallets reference users by
    --      email that change has to propagate to every referencing table.
    --      With an id it is one row: UPDATE users SET email=? WHERE id=7
    --   2. it puts PII in URLs, browser history, proxy logs and Referer
    --      headers — the hardest places to erase from
    --   3. 8 sequential bytes per reference instead of ~25 random ones
    --
    -- ALWAYS, not BY DEFAULT: it REJECTS an application trying to supply its
    -- own id. Not SERIAL — that is the legacy spelling and only a default.
    --
    -- "Primary key: what the row IS. Unique constraint: a promise about what
    --  it HAS."
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- UNIQUE IS WHY TODAY HAPPENED. The service already has
    -- `if (findByEmail(email).isPresent()) throw new DuplicateEmailException`.
    -- That is a look-then-act with a gap: two concurrent signups both read
    -- "empty", both pass the check, and both insert.
    --
    -- The Java check stays — it produces a readable 409. It is a CONVENIENCE.
    -- This word is the DEFENCE.
    --
    -- "Never let the only copy of a correctness guarantee live in application
    --  code. Application code races. Constraints don't."
    email VARCHAR(255) NOT NULL UNIQUE,

    first_name VARCHAR(100) NOT NULL,

    last_name VARCHAR(100) NOT NULL,

    -- Plaintext today, and that is a real debt with a due date: Day-05/06
    -- introduces BCrypt and renames this to password_hash. 255 is already
    -- sized for a BCrypt hash (60 chars) so that migration touches the name,
    -- not the type.
    password VARCHAR(255) NOT NULL,

    -- THE ONLY NULLABLE COLUMN IN THIS TABLE, and the only one that should be.
    -- A user genuinely might not have a profile picture; there is no honest
    -- default, and "" would be a lie that every reader has to remember to
    -- decode.
    --
    -- Nullability is a TYPE decision, and SQL's default is the wrong one:
    --     VARCHAR(255)            "a string, or nothing"
    --     VARCHAR(255) NOT NULL   "a string"
    -- Every NOT NULL above is a null check the Java never has to write.
    -- This is the column the User model already returns as Optional<String> —
    -- same idea, enforced one layer down.
    image VARCHAR(512),

    -- TIMESTAMPTZ, never TIMESTAMP. A plain TIMESTAMP stores a wall-clock
    -- reading with no record of which clock produced it. Under DST an hour
    -- occurs TWICE, and for a ledger that means two transfers an hour apart
    -- can be indistinguishable in ordering. TIMESTAMPTZ stores a real point on
    -- the timeline.
    --
    -- DEFAULT now() means the DATABASE decides, not the application. With
    -- several app instances running on machines whose clocks disagree, the
    -- database is the one clock every row shares.
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
