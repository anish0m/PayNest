package com.paynest.wallet.model;

import com.paynest.user.model.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

import com.paynest.wallet.exception.WalletFrozenException;

@Entity
@Table(name = "wallets")
@Getter
@ToString(exclude = "user")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true, updatable = false)
    private User user;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletStatus status;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // -----------------------------------------------------------------------
    //  DAY-10, STEP 2(ii) — declare the version field on the entity.
    //
    //  V9 added the column. Nothing reads it yet, so nothing has changed: step
    //  1's test is still red for the same reason. THIS is the step that turns
    //  the column into behaviour.
    //
    //  WHERE IT GOES. Above this `protected Wallet()` constructor, with the
    //  other fields — after `createdAt`. Order does not matter to JPA; it
    //  matters to the reader, and `version` belongs with the other
    //  database-managed column rather than among the business ones.
    //
    //  WHAT TO WRITE. Three lines:
    //
    //    @Version
    //    @Column(name = "version", nullable = false)
    //    private Long version;
    //
    //  And one import: jakarta.persistence.Version. ⚠️ NOT
    //  org.springframework.data.annotation.Version — Spring Data has an
    //  annotation of the same simple name, your IDE will offer both, and the
    //  wrong one compiles cleanly and does absolutely nothing. A silent
    //  no-op import is the worst failure shape available here, because the
    //  test would stay red and you would go looking at the migration.
    //
    //  WHY Long AND NOT long. Use the wrapper. Hibernate needs to distinguish
    //  "this entity has never been persisted" (version == null) from "this
    //  entity is at revision zero" (version == 0L). A primitive `long` cannot
    //  be null, so every new Wallet looks like it is already at revision 0 and
    //  Hibernate can no longer tell a fresh object from a detached one — it
    //  then issues an UPDATE where an INSERT was due. This is a real trap and
    //  it does not fail at compile time.
    //
    //  WHY NO GETTER, AND WHY @Getter ON THE CLASS IS FINE. Lombok's class-level
    //  @Getter will generate getVersion() for you, which is harmless. But
    //  resist the urge to add a setter or to touch this field anywhere in
    //  business code: it is written by Hibernate, read by Hibernate, and
    //  meaningless to every other reader. ⚠️ And do NOT add it to
    //  WalletResponse — a revision counter is an internal implementation
    //  detail, and putting it in an API response makes it a contract you then
    //  cannot change.
    //
    //  ⚠️ WHAT THIS DOES NOT TOUCH, and it is the reason today's fix is narrow.
    //  @Version only participates in updates that go through the DIRTY CHECK —
    //  Hibernate loads the entity, compares fields at flush, and writes an
    //  UPDATE it composed itself. `debitIfSufficient` in
    //  SpringDataWalletRepository is a @Modifying bulk UPDATE: a hand-written
    //  statement that Hibernate executes verbatim. It will NOT bump the version
    //  and the version will NOT appear in its WHERE clause.
    //
    //  That sounds like a hole. It is not — it is the asymmetry you traced in
    //  step 1. `debitIfSufficient` is
    //      UPDATE wallets SET balance = balance - :amt
    //       WHERE id = :id AND balance >= :amt
    //  which asks and acts in ONE statement, with no gap between the read and
    //  the write. It is already a compare-and-set; `rows == 0` IS the
    //  rejection. It does not need a version because it never holds a stale
    //  value. The credit side — findById, credit(), flush at commit — is a
    //  read-modify-write WITH a gap, and that is the only side @Version is
    //  defending.
    //
    //  ⚠️ CONSEQUENCE TO EXPECT, not a bug: a debit no longer increments the
    //  version, so the counter is not a count of all changes to the row. If you
    //  ever write a test asserting "version == 2 after two transfers", it will
    //  fail, and the test will be wrong, not the code.
    //
    //  VERIFY — and the FIRST check is a boot, not a test.
    //
    //    (a) `./mvnw -q test -Dtest=WalletRepositoryIT` — anything that boots
    //        the jpa profile is enough. ddl-auto=validate now compares this
    //        field against V9's column. A name or type mismatch fails at
    //        STARTUP with a schema-validation error, which is the cheapest
    //        place to catch a typo in either file. If this fails, suspect V9's
    //        column name before you suspect this line.
    //
    //    (b) `./mvnw -q test -Dtest=TransferConcurrencyIT` — ⚠️ EXPECT IT TO
    //        STILL FAIL, and expect the failure to have CHANGED SHAPE. It
    //        should no longer be the balance assertion reporting 30.0000. One
    //        of the two threads should now throw, so the test should die on an
    //        assertNull — "first transaction failed: ...
    //        ObjectOptimisticLockingFailureException".
    //
    //        Read which assertion catches it. Those are two different worlds:
    //        still-30.0000 means the version is not being applied (go back to
    //        the import question above); a failed assertNull means detection
    //        works and the money is still not moving. The second is the
    //        expected state at the end of step 2, and step 1's test is the
    //        WRONG test for it — it was written to assert no money is lost,
    //        and "one caller got an exception" is not that. Fixing it is a
    //        later step's job, not a reason to edit the test now.
    //
    //    (c) Confirm the counter actually moves, because (b) passing on an
    //        exception does not prove the increment:
    //          SELECT id, balance, version FROM wallets ORDER BY id;
    //        against `paynest` after a deposit through the running app. A
    //        deposit goes through the dirty check, so version should go 0 → 1.
    // -----------------------------------------------------------------------

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Wallet() {
    }

    public Wallet(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        this.user = user;
        this.balance = BigDecimal.ZERO;
        this.status = WalletStatus.ACTIVE;
    }

    public void changeStatus(WalletStatus newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Wallet status cannot be null");
        }

        if (this.status == WalletStatus.CLOSED && newStatus != WalletStatus.CLOSED) {
            throw new WalletFrozenException(id, status);
        }

        this.status = newStatus;
    }

    public void credit(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        BigDecimal newBalance = this.balance.subtract(amount);

        // Backstop mirroring wallets_balance_non_negative; not the funds gate.
        if (newBalance.signum() < 0) {
            throw new IllegalStateException("Wallet balance cannot be negative");
        }

        this.balance = newBalance;
    }
}
