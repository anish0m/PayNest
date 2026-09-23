-- ===========================================================================
--  DAY-05, STEP 2.5 — V4: categories, and the project's first N:N.
--
--  NEW REQUIREMENT, added 22 Sep 2026. Not from MISSION.pdf — yours.
--
--  The case that justifies it, in your words: you send your niece several
--  allowances — food, skincare, bus fare. One transfer may carry MANY
--  categories ("allowance" AND "food"). One category ("allowance") spans
--  MANY transfers, every month. Many-to-many in both directions, and
--  neither side can hold the other's key.
--
--  WHY THIS IS A REAL N:N AND NOT A DISGUISED 1:N — worth being able to
--  answer, because it is the first question anyone will ask:
--
--      one transfer  -> many categories    (allowance + food)
--      one category  -> many transfers     (12 monthly allowances)
--
--  If EITHER direction were "one", this would be a foreign key on one of
--  the two tables and no third table would exist. It is the fact that BOTH
--  directions are "many" that makes the link table unavoidable — there is
--  no column you could put on `transactions` that holds several category
--  ids, and none on `categories` that holds several transfers.
--
--  A relational database cannot express N:N directly. It expresses it as
--  TWO 1:N relationships pointing INTO a third table. That third table is
--  the whole trick, and it is why you already know how to build this:
--  it is two of the thing you did in V2.
-- ===========================================================================


-- ---------------------------------------------------------------------------
--  4a. The `categories` table.
--
--  Columns you need:
--      id         BIGINT, generated always as identity, primary key
--                 (same form as users.id and wallets.id — be consistent)
--      name       VARCHAR(50) NOT NULL UNIQUE
--      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
--
--  WHY name IS UNIQUE. Two rows both called "allowance" would silently
--  split your reporting in half: some transfers tagged to id 3, some to
--  id 9, and every "show me allowances" query quietly wrong. Same reasoning
--  as users.email — this is the thing a human means when they say the
--  category, so the database should refuse to hold it twice.
--
--  Note this also hands you the index for free, exactly as it did on users.
--
--  WHY NOT AN ENUM, or a CHECK constraint listing the allowed names?
--  Because categories are DATA, not code. "skincare" was not in anyone's
--  head when the schema was written, and adding it should be an INSERT, not
--  a migration and a redeploy. The rule: if a business user could plausibly
--  want to add one on a Tuesday, it is a row.
-- ---------------------------------------------------------------------------

CREATE TABLE categories
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
--  4b. The link table — and read this before you write it, because your
--      V2 already forced a decision here.
--
--  THE QUESTION: what does a category attach TO?
--
--  Your V2 comment says it exactly: "a transfer is an invariant, not a
--  row." Double-entry means ONE transfer is TWO rows in `transactions` —
--  a negative one on the sender's wallet, a positive one on the
--  receiver's, sharing a `transfer_id` UUID and summing to zero.
--
--  So "tag this transfer as allowance" must NOT attach to transactions.id.
--  If it did, you would have to tag both rows, and nothing would stop them
--  disagreeing — the debit tagged "food", the credit tagged "skincare",
--  for one single transfer. An impossible state you would then have to
--  write code to prevent.
--
--  ⚠️ SO THE LINK TABLE REFERENCES transfer_id, NOT transactions.id.
--  The category belongs to the TRANSFER (the business event), not to
--  either half of its bookkeeping.
--
--  That is why this file is named transfer_categories and not
--  transaction_categories. Name it after what it actually links.
--
--  ---------------------------------------------------------------
--  THE CONSEQUENCE, and it is the interesting one:
--
--  transactions.transfer_id is NOT UNIQUE (deliberately — two rows share
--  it). A foreign key can only point at a UNIQUE or PRIMARY KEY column.
--  So you CANNOT write REFERENCES transactions(transfer_id) — Postgres
--  will reject it with:
--
--      there is no unique constraint matching given keys for referenced
--      table "transactions"
--
--  You have two honest options. PICK ONE AND WRITE DOWN WHY:
--
--    OPTION A — no FK on transfer_id. Store it as a plain UUID NOT NULL.
--      Honest about the limitation; referential integrity for this column
--      is then NOT enforced, and you have accepted that. Simple, and it
--      matches the fact that `transfer_id` is a grouping key rather than a
--      row identifier.
--
--    OPTION B — introduce a `transfers` table (id UUID PRIMARY KEY, plus
--      whatever a transfer knows: created_at, maybe a note), make
--      transactions.transfer_id REFERENCE it, and have this link table
--      reference it too. Correct, and a bigger change — it touches V2's
--      table and adds a concept Day-08 will need anyway.
--
--  RECOMMENDATION: Option A today. It is one migration, it keeps step 2.5
--  a detour rather than a redesign, and the missing FK is a KNOWN, WRITTEN
--  debt rather than an oversight. Day-08 builds the transfer service and
--  is the honest moment for a `transfers` table.
--
--  Write your choice as a comment in this file. A schema decision that
--  exists only in someone's memory is the thing this whole project keeps
--  teaching you to avoid.
--
--  ---------------------------------------------------------------
--  COLUMNS for transfer_categories (assuming Option A):
--
--      transfer_id  UUID   NOT NULL
--      category_id  BIGINT NOT NULL REFERENCES categories(id)
--      created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
--
--  ⚠️ AND THE LINE THAT MAKES IT AN N:N RATHER THAN A PILE OF ROWS:
--
--      PRIMARY KEY (transfer_id, category_id)
--
--  A COMPOSITE primary key — two columns, one key. Three things it buys,
--  and the first is the one people miss:
--
--    1. It makes tagging IDEMPOTENT. Tag a transfer "food" twice and the
--       second INSERT is refused by the database. Without it you get
--       duplicate link rows, and every JOIN silently double-counts —
--       a transfer appears twice in its own category listing.
--       Same shape as users.email UNIQUE: application code races, the
--       constraint does not.
--
--    2. There is no surrogate `id` column, ON PURPOSE. This table has no
--       identity of its own — a link row IS its two endpoints, and an
--       extra id would be a second way to name the same fact. Contrast
--       wallets, where V2 gave you an id precisely because a wallet is a
--       thing in its own right.
--
--    3. It creates an index on (transfer_id, category_id) for free, which
--       answers "what categories does this transfer have?" without a scan.
--
--  You do NOT need ON DELETE CASCADE here today; deleting categories is
--  step 2.5's `delete` endpoint and you will meet the FK there deliberately.
-- ---------------------------------------------------------------------------

CREATE TABLE transfer_categories
(
    transfer_id UUID        NOT NULL,
    category_id BIGINT      NOT NULL REFERENCES categories (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (transfer_id, category_id)
);

-- ---------------------------------------------------------------------------
--  4c. One more index — and this is the V2 lesson repeating.
--
--  The composite PK indexes (transfer_id, category_id) — LEFT TO RIGHT.
--  That answers "categories for this transfer" fast.
--
--  It does NOT answer the reverse: "every transfer in the allowance
--  category" — which is your niece's twelve monthly allowances, i.e. the
--  query your whole example is about. A B-tree on (a, b) cannot be used to
--  look up by `b` alone, for the same reason a phone book sorted by
--  surname cannot find everyone with the first name "Anishom".
--
--  Your V2 comment made exactly this point about (wallet_id, created_at).
--  Same rule, second sighting.
--
--  So add:   CREATE INDEX ... ON transfer_categories (category_id);
--
--  Name it in the same style you used in V2 (idx_<table>_<columns>).
-- ---------------------------------------------------------------------------

CREATE INDEX idx_transfer_categories_category_id
    ON transfer_categories (category_id);

-- ---------------------------------------------------------------------------
--  4d. Seed a few categories, and document the tables.
--
--  INSERT INTO categories (name) VALUES ('allowance'), ('food'), ... ;
--
--  Pick 4-5 that match your example. Seeding reference data in a migration
--  is legitimate — unlike user data, these rows are part of what the
--  application MEANS, and a fresh clone with an empty categories table
--  cannot tag anything.
--
--  Then a COMMENT ON TABLE for each, as you did in V1/V2. For
--  transfer_categories, say what it links and note the Option A/B decision
--  in one line.
-- ---------------------------------------------------------------------------

INSERT INTO categories (name)
VALUES
    -- Income
    ('Allowance'),
    ('Savings & Transfers'),
    ('Investment Income'),

    -- Housing & Living
    ('Rent'),
    ('Utilities'),
    ('Household Supplies'),
    ('Maintenance'),

    -- Food & Dining
    ('Groceries'),
    ('Dining Out'),

    -- Transportation
    ('Public Transit'),
    ('Fuel'),

    -- Health & Personal Care
    ('Personal Care & Skincare'),
    ('Health & Medical'),
    ('Fitness'),

    -- Family & Lifestyle
    ('Gifts'),
    ('Donations');

COMMENT
ON TABLE categories IS
    'Reference data for human-meaningful transfer categories such as allowance, food, and bus fare.';

COMMENT
ON TABLE transfer_categories IS
    'Links transfer UUIDs to categories. Option A: transfer_id is stored as a plain UUID until a transfers table exists.';

-- ===========================================================================
--  WHAT YOU CAN DEMONSTRATE ONCE THIS RUNS — nothing to write here.
--
--    \d categories              -> UNIQUE on name created an index
--    \d transfer_categories     -> composite PK, and the FK to categories
--
--    -- the N:N, proven in one query: a transfer with two categories
--    SELECT tc.transfer_id, c.name
--      FROM transfer_categories tc
--      JOIN categories c ON c.id = tc.category_id
--     WHERE tc.transfer_id = '<some uuid>';
--
--    -- and the other direction: every transfer in one category
--    SELECT c.name, COUNT(*)
--      FROM transfer_categories tc
--      JOIN categories c ON c.id = tc.category_id
--     GROUP BY c.name;
--
--    -- idempotency, provable: run the same INSERT twice
--    INSERT INTO transfer_categories (transfer_id, category_id) VALUES (...);
--    INSERT INTO transfer_categories (transfer_id, category_id) VALUES (...);
--    -- second one: duplicate key value violates unique constraint
--
--  That last one is the demo worth showing a mentor. It is not "look, a
--  join table" — it is "the database refuses to let the same fact be
--  recorded twice, and here is it refusing."
-- ===========================================================================
