-- ===========================================================================
-- V2__create_wallets_and_transactions.sql
--
-- V2, not an edit to V1. V1 has run, so Flyway holds its checksum and would
-- refuse to start if it changed. Every schema change from here is a new file.
--
-- This is where PayNest stops being CRUD. Two tables, a foreign key, money,
-- and a ledger.
-- ===========================================================================

CREATE TABLE wallets (

    -- Same reasoning as users.id. Note the wallet gets its OWN identity rather
    -- than reusing user_id as the primary key — a 1:1 could be modelled either
    -- way, and this way survives the day a user is allowed a second wallet.
     id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- THE FOREIGN KEY GOES ON THE MANY SIDE, which is backwards from how Java
    -- would model it. In Java the reach is for `class User { List<Wallet>
    -- wallets; }` — the container holds the contained. In SQL the child points
    -- at the parent: a wallet knows its user, and a user does not contain its
    -- wallets. Nothing is stored on the users row at all.
    --
    -- REFERENCES buys REFERENTIAL INTEGRITY: it becomes IMPOSSIBLE to insert a
    -- wallet for user 99 when there is no user 99. Not "the application checks
    -- and would reject it" — impossible. It also blocks DELETE of a user who
    -- still has a wallet, which is the database refusing to create an orphan.
    --
    -- UNIQUE is what makes this 1:1 rather than 1:N. Without it, nothing stops
    -- a second row pointing at the same user, and "one wallet per user" would
    -- live only in whichever Java method remembered to check. Same argument as
    -- users.email: a policy in code, or a guarantee in the schema.
    --
    -- Day-15 is where this line gets interesting: in the microservice split,
    -- wallets and users live in different databases and this FK CANNOT EXIST.
    -- A foreign key is enforced by one database process. That is the concrete
    -- thing given up for independent deployment.
     user_id BIGINT NOT NULL UNIQUE REFERENCES users(id),

    -- NEVER float/double, in any language. 0.1 + 0.2 = 0.30000000000000004
    -- under IEEE 754 — not a bug, the specified behaviour of binary fractions.
    -- NUMERIC is exact decimal arithmetic: the database stores 0.30, not the
    -- nearest binary approximation to it.
    --
    -- The real cost is not the lost fraction. It is losing the ability to tell
    -- an arithmetic error from an attack: once totals drift on their own, a
    -- discrepancy proves nothing. (Vancouver Stock Exchange, 1980s — the index
    -- drifted to roughly half its true value in 22 months.)
    --
    -- (19,4) = 19 significant digits, 4 after the point. Four, not two, so
    -- intermediate results (a 2.5% fee on 10.01) keep their precision and the
    -- rounding happens ONCE, at the end. Rounding at every step compounds the
    -- error.
    --
    -- A stored balance, by decision: fast reads, plus a Day-13 reconciliation
    -- job that recomputes from the ledger and ALERTS on drift rather than
    -- silently fixing it — a job that quietly repairs drift hides the bug that
    -- caused it.
     balance NUMERIC(19,4) NOT NULL DEFAULT 0,

     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A TABLE-LEVEL constraint: its own entry in the comma-separated list,
    -- because it is a rule about the row rather than a property of one column.
    --
    -- This is the BACKSTOP, not the mechanism. Day-13's transfer uses a
    -- conditional write — UPDATE ... WHERE id=? AND balance >= ? — which does
    -- the check and the write in ONE statement, closing the read-then-write
    -- gap. This CHECK is the second lock on the door: if any future code path
    -- forgets, the database still refuses.
    --
    -- Deliberately belt and braces. Money is the place for it.
     CONSTRAINT wallets_balance_non_negative CHECK (balance >= 0)
);


-- THE LEDGER. Double-entry: every transfer writes TWO signed rows sharing one
-- transfer_id, and they sum to zero. Money is never created or destroyed,
-- only moved.
--
-- The consequence worth internalising: a transfer is an INVARIANT, not a row.
-- One leg alone is not half a transfer — it is corruption. That is what makes
-- the Day-13 transaction boundary non-negotiable.
CREATE TABLE transactions (

    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- The two legs of one transfer share this. NOT unique — exactly two rows
    -- carry each value, and that is the point.
    --
    -- UUID rather than BIGINT because the application generates it BEFORE the
    -- insert: it needs a name for the transfer in order to write both legs
    -- under it. An identity column is assigned by the database, which is too
    -- late. This is also the column Day-11's Idempotency-Key work builds on.
    transfer_id UUID NOT NULL,

    -- Which wallet this leg moves. 1:N here, not 1:1 — a wallet has many
    -- transactions — so NO UNIQUE on this one. That single word is the entire
    -- difference between the two relationships in this file.
    wallet_id BIGINT NOT NULL REFERENCES wallets(id),

    -- SIGNED: negative leaving, positive arriving. No `direction` column and
    -- no separate from_/to_ columns — the sign IS the direction, and SUM() of
    -- a valid transfer's two rows is exactly zero.
    --
    -- Note there is NO CHECK (amount > 0) here. It would be wrong: half of all
    -- legitimate rows are negative by design.
    --
    -- Compare with the denormalised alternative — transactions(from_email,
    -- from_name, to_email, ...). Change a user's name and every historical row
    -- disagrees with every other. "Every fact lives in exactly one place":
    -- wallet_id points at the fact, it does not copy it.
    amount NUMERIC(19,4) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- PRIMARY KEY and UNIQUE create their B-tree indexes automatically. A FOREIGN
-- KEY DOES NOT.
--
-- Postgres indexes the column being pointed AT (wallets.id, already a PK) but
-- not the column doing the pointing. So transactions.wallet_id has no index
-- unless it is written here, and "show me this wallet's transactions" is a
-- sequential scan of the whole ledger. This is the most commonly forgotten
-- index in production systems, and it is invisible until the table is big.
--
-- COMPOSITE, and the column order matters. This serves both "this wallet's
-- rows" and "this wallet's rows, newest first" from one index, because the
-- rows for a given wallet_id are already stored in created_at order — so the
-- statement-history page needs no sort at all. Reversed, (created_at,
-- wallet_id) would be near-useless for the query that actually runs: a phone
-- book sorted by surname cannot find a forename.
--
-- The cost, so this is a trade and not free: every INSERT must also update
-- every index, and each one occupies disk. An index is a bet that this table
-- is read this way far more often than it is written.
CREATE INDEX idx_transactions_wallet_created_at
    ON transactions (wallet_id, created_at DESC);

-- For fetching both legs of one transfer — reconciliation, and showing a user
-- what a single transfer did.
CREATE INDEX idx_transactions_transfer_id
    ON transactions (transfer_id);


-- ---------------------------------------------------------------------------
-- NOT in this file, deliberately:
--
--   * No index on wallets.user_id — the UNIQUE constraint already made one.
--     Adding a second would be pure cost. Check before writing an index.
--   * No ON DELETE CASCADE. Deleting a user who has a wallet SHOULD fail
--     loudly. Cascading deletes on financial records destroy the audit trail,
--     which is usually the one thing that must survive.
-- ---------------------------------------------------------------------------
