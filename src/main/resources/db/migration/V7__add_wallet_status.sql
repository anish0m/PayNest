-- ===========================================================================
--  DAY-07, STEP 1 — V7: a wallet can be switched off.
--
--  WHY V7. V6 (add_role) is the highest existing version. Same rule as always:
--  the number is a position in YOUR history, not a label for the change. Note
--  persona's equivalent is V4 and the microservice's is V1 — three independent
--  Flyway histories, three different numbers for the same idea, and they must
--  NOT be reconciled. A Day-07 test in persona hardcoded "expect 2 migrations"
--  and broke the moment this divergence arrived; it was rewritten to ask *did
--  everything apply, and did anything fail?* instead of counting.
--
--  WHY THIS COLUMN EXISTS AT ALL. Today's real subject is the TRANSACTION
--  BOUNDARY (step 7: a user and a wallet, or neither). This column is the
--  smaller half: a wallet that exists but may not move money.
--
--  The question it answers is "what do you do when an account must stop
--  trading but cannot be deleted?" — a fraud hold, a dispute, an account
--  closure with a non-zero balance. DELETE is not available: V2 deliberately
--  declined ON DELETE CASCADE, and destroying a financial record to express
--  "suspended" loses the audit trail. So the state goes in a column.
--
--  ⚠️ BLAST RADIUS, unlike V6: nearly none. Your wallets table has no
--  repository, no entity, and no endpoint yet — steps 2-6 build all of that on
--  top of this file. V6's warning was that four UserRepository
--  implementations had to agree about one column. Here you are adding the
--  column BEFORE the code that reads it, which is the easier direction and
--  worth noticing as a sequencing choice rather than luck.
-- ===========================================================================


-- ---------------------------------------------------------------------------
--  1a. Add the column.
--
--  The three decisions are the same shape as V6's, and for the same reasons —
--  re-derive them rather than copying, because one of them differs:
--
--  NOT NULL. A wallet whose status is unknown is a wallet where "may this
--  move money?" has no answer, and the null branch gets written as "if null,
--  allow" by someone in a hurry. There is no such thing as a wallet with no
--  status.
--
--  DEFAULT 'ACTIVE'. ⚠️ THIS IS WHERE IT DIFFERS FROM V6, AND THE DIFFERENCE
--  IS WORTH SITTING WITH. V6's argument was that a silent assignment to
--  existing rows must go in the LEAST privileged direction — DEFAULT 'ADMIN'
--  would have worked perfectly and silently granted everyone everything.
--  Apply that argument literally here and the default would be 'FROZEN'.
--
--  It is still 'ACTIVE', and the reason is that the safe direction depends on
--  what the value MEANS, not on a rule about defaults. 'ADMIN' grants a power
--  nobody had. 'ACTIVE' preserves a power everyone already had — these
--  wallets have been spending money since V2. DEFAULT 'FROZEN' would not fail
--  safe; it would silently suspend every existing account, which is an outage
--  dressed as caution. The lesson is that "fail closed" means *do not grant
--  what was not granted*, which is not the same as *pick the most
--  restrictive value*.
--
--  VARCHAR(20), not a Postgres ENUM type. Same trade as V6 and the same
--  honest cost: a real system grows a lookup table, the CHECK below is what
--  keeps the cheap version from rotting. One extra reason here — a Postgres
--  ENUM cannot have a value REMOVED, and 'FROZEN' is exactly the kind of
--  state a product changes its mind about.
--
--  Syntax:  ALTER TABLE <table> ADD COLUMN <name> <type> NOT NULL DEFAULT <v>;
--
--  Write it: table `wallets`, column `status`, VARCHAR(20), NOT NULL, default
--  'ACTIVE'.
-- ---------------------------------------------------------------------------


ALTER TABLE wallets
    ADD COLUMN status VARCHAR(20)
        NOT NULL DEFAULT 'ACTIVE';


-- ---------------------------------------------------------------------------
--  1b. Constrain what may go in it.
--
--  Without this, `status` is free text and every one of these inserts works:
--
--      'active'    -- lowercase. No Java comparison will match it.
--      'FROZEN '   -- trailing space. Also will not match.
--      'SUSPENDED' -- a state no code in this project has heard of.
--      ''          -- NOT NULL is satisfied. Empty string is not null.
--
--  ⚠️ NOTE THE FAILURE DIRECTION HAS FLIPPED SINCE V6. A bad `role` failed
--  CLOSED — hasRole('ADMIN') returned false and the user quietly had no
--  privileges. A bad `status` fails OPEN: the code you write in step 5 will
--  ask "is this FROZEN?", get false for 'frozen', and ALLOW THE WITHDRAWAL.
--  Same class of typo, same silence, opposite consequence, and this one moves
--  money. That asymmetry is the reason this CHECK is not optional ceremony.
--
--  Syntax:
--      ALTER TABLE <table> ADD CONSTRAINT <name> CHECK (<condition>);
--
--  Name it `wallets_status_valid` — a named constraint appears in the error
--  message when it fires; `wallets_check1` tells the reader nothing. Condition:
--  status IN ('ACTIVE', 'FROZEN', 'CLOSED').
--
--  Three values, and know what each is FOR before step 5 asks you to enforce
--  them: ACTIVE moves money both ways; FROZEN moves neither (a hold — expected
--  to be temporary); CLOSED is terminal and the row survives for the audit
--  trail. Step 5's rule is that FROZEN rejects a credit and CLOSED rejects
--  everything, with 409 rather than 500 — the request was well-formed, the
--  state refused it.
-- ---------------------------------------------------------------------------


ALTER TABLE wallets
    ADD CONSTRAINT wallets_status_valid
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'));


-- ---------------------------------------------------------------------------
--  1c. Document it in the database.
--
--  Same as V3 and V6, same audience: whoever has a psql prompt at 3am and has
--  never opened this repo.
--
--  The fact worth recording here is the one invisible from SQL: this column is
--  read by Java as an ENUM, and the mapping is `@Enumerated(EnumType.STRING)`.
--  ⚠️ JPA's DEFAULT IS ORDINAL, which would store 0/1/2 — and then reordering
--  the Java enum silently reinterprets every existing row. ACTIVE becomes
--  FROZEN with no migration, no error and no way to tell from the data. The
--  column is VARCHAR partly so that this trap is impossible to fall into
--  unnoticed: an ORDINAL mapping against a VARCHAR(20) with this CHECK fails
--  loudly on the first write instead of corrupting quietly. Step 2 is where
--  that annotation gets written.
--
--  Write a COMMENT ON COLUMN wallets.status saying what it holds, that it is
--  constrained to those three values, what each one permits, and that Java
--  maps it with EnumType.STRING and must never use ORDINAL.
-- ---------------------------------------------------------------------------


COMMENT ON COLUMN wallets.status
        IS 'Wallet lifecycle status: ACTIVE, FROZEN, or CLOSED. ACTIVE permits money movement; FROZEN permits neither credits nor debits; CLOSED is terminal and rejects all movement while preserving the audit trail. Java maps this column with EnumType.STRING; never use ORDINAL.';


-- ===========================================================================
--  VERIFY — and verify more than "it ran".
--
--    1. Boot the app and confirm V7 applied AND that nothing failed:
--         SELECT version, description, success FROM flyway_schema_history
--         ORDER BY installed_rank DESC LIMIT 3;
--       ⚠️ Assert on `success`, not on a row count. You have 6 migrations
--       today and will have more; a hardcoded count is a test that breaks for
--       the wrong reason.

--    2. Confirm existing rows were backfilled, not left null:
--         SELECT status, count(*) FROM wallets GROUP BY status;
--       ⚠️ CHECK THE COUNT IS NON-ZERO FIRST. A GROUP BY over an empty table
--       returns no rows, and "no rows" reads as a pass. `wallets` may well be
--       EMPTY right now — nothing creates a wallet until step 7 — so this
--       query proving nothing is the LIKELY outcome, not the edge case. If it
--       comes back empty, insert a row by hand for a user that exists and
--       confirm it lands as ACTIVE without status being mentioned:
--         INSERT INTO wallets (user_id) VALUES (
--           (SELECT id FROM users WHERE email = 'd7.user@example.com'));
--         SELECT id, user_id, balance, status FROM wallets;

--    3. PROVE THE CONSTRAINT ACTUALLY FIRES. An untested constraint is
--       indistinguishable from a comment:
--         UPDATE wallets SET status = 'frozen' WHERE id = (SELECT min(id) FROM wallets);
--       Expect: ERROR ... violates check constraint "wallets_status_valid".
--       ⚠️ If that UPDATE reports `UPDATE 0`, it did not pass — it matched no
--       rows, because the table is empty. That is the same trap as (2) wearing
--       a different hat: a statement that affects nothing cannot violate
--       anything. You need the row from (2) in place first, and you need to
--       see the word ERROR.

--    4. Lowercase is the realistic typo, so prove the valid values still work:
--         UPDATE wallets SET status = 'FROZEN' WHERE id = (SELECT min(id) FROM wallets);
--         UPDATE wallets SET status = 'ACTIVE' WHERE id = (SELECT min(id) FROM wallets);
--       Both should report UPDATE 1. A CHECK that rejects everything would
--       pass test (3) perfectly.

--  NOTHING IN THE TEST SUITE WILL NOTICE ANY OF THIS. No test asserts on
--  schema constraints, and `ddl-auto=validate` will not run until an entity
--  exists in step 2. psql is the verification for this step, the same way curl
--  was for step 0 — where two of three readings looked like passes and were
--  not.
-- ===========================================================================
