package com.paynest.category.model;

// =========================================================================
//  DAY-05, STEP 2.5b — the Category entity.
// =========================================================================
//  Imports you will need:
//      jakarta.persistence.{Entity, Table, Id, GeneratedValue,
//                           GenerationType, Column}
//      lombok.{Getter, Setter, AccessLevel, ToString}
//
//  This is the FOURTH time you have written an entity, so most of it is
//  muscle memory now. What is new is at the bottom — read that part first
//  if you only read one section.
// =========================================================================

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Locale;

// -------------------------------------------------------------------------
//  CLASS-LEVEL ANNOTATIONS
//
//  @Entity, @Table(name = "categories"), @Getter, and @ToString.
//
//  ⚠️ NOTE WHAT IS MISSING: no class-level @Setter.
//
//  On User you put @Setter at class level and then had to CLAW IT BACK on
//  four fields with @Setter(AccessLevel.NONE) — id, createdAt, email,
//  image. Four exceptions to one rule means the rule was wrong.
//
//  Here there is exactly ONE mutable field (name), so put @Setter on that
//  field alone. Default to immutable, open up deliberately — rather than
//  default to open and remember to close. Same instinct as the DTO having
//  no `id` field: the safe thing should require no vigilance.
// -------------------------------------------------------------------------
@Entity
@Table(name = "categories")
@Getter
@ToString
public class Category {

    // ---------------------------------------------------------------------
    //  id — @Id, @GeneratedValue(strategy = IDENTITY), @Column(name = "id")
    //
    //  IDENTITY, not AUTO, for the reason you met on Day-04: AUTO picks
    //  SEQUENCE on Postgres and your V4 said GENERATED ALWAYS AS IDENTITY.
    //  ddl-auto: validate would catch the disagreement at startup, but
    //  knowing WHY beats being caught.
    //
    //  Type Long, not long — null means "not saved yet", and a primitive
    //  would default to 0, which is indistinguishable from a real id.
    // ---------------------------------------------------------------------
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ---------------------------------------------------------------------
    //  name — @Column(name = "name", nullable = false, unique = true,
    //                 length = 50)
    //         plus @Setter on this field only.
    //
    //  length = 50 must MATCH V4 exactly. It is a claim about the schema,
    //  and validate audits it at boot.
    //
    //  unique = true here is documentation, not enforcement — the UNIQUE
    //  constraint in V4 is what actually holds. Hibernate only uses this
    //  when generating DDL, which you never let it do. Write it anyway:
    //  the entity should describe the table truthfully.
    // ---------------------------------------------------------------------
    @Setter
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    // ---------------------------------------------------------------------
    //  createdAt — Instant, mapped:
    //      @Column(name = "created_at", nullable = false,
    //              insertable = false, updatable = false)
    //
    //  insertable = false is the Day-04 lesson, and it is the one you
    //  reasoned out yourself: the column has DEFAULT now(), so leaving it
    //  OUT of the INSERT is what lets the database supply it. Without it
    //  Hibernate validates the null field in Java, before the INSERT, and
    //  refuses — DEFAULT now() never gets a chance.
    //
    //  No setter. The application reads this value; it never writes it.
    // ---------------------------------------------------------------------
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    // ---------------------------------------------------------------------
    //  protected no-arg constructor — Hibernate needs it, application code
    //  must not have it. Same as User.
    // ---------------------------------------------------------------------
    protected Category() {
    }

    // ---------------------------------------------------------------------
    //  public Category(String name) — the only constructor the app uses.
    //  Route it through setName so construction and mutation share one
    //  validation path (your User lesson, applied without being told).
    // ---------------------------------------------------------------------
    public Category(String name) {
        setName(name);
    }

    Category(Long id, String name, Instant createdAt) {
        this(name);
        this.id = id;
        this.createdAt = createdAt;
    }

    // ---------------------------------------------------------------------
    //  setName(String name) — validate: not null, not blank, and consider
    //  normalising. "Food", "food" and " food " are the same category to a
    //  human but three different rows to a UNIQUE constraint.
    //
    //  Suggestion: trim() and toLowerCase() before storing. Then the
    //  constraint enforces what you actually mean rather than what was
    //  typed. Write a comment saying you did it and why — this is a real
    //  decision, not a formatting tweak.
    // ---------------------------------------------------------------------
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be null or blank");
        }

        this.name = name.trim().toLowerCase(Locale.ROOT);
    }

    // =====================================================================
    //  ⚠️ THE PART THAT IS ACTUALLY NEW — AND THE DECISION TO MAKE.
    // =====================================================================
    //  You may expect a field like:
    //
    //      @ManyToMany
    //      @JoinTable(name = "transfer_categories", ...)
    //      private Set<Transaction> transactions;
    //
    //  DO NOT WRITE IT. Three reasons, in increasing order of importance:
    //
    //  1. THERE IS NO Transaction ENTITY YET. It does not exist until
    //     Day-08. You cannot map an association to a class you have not
    //     written.
    //
    //  2. THE LINK IS TO transfer_id, NOT TO A ROW. Your V4 attaches
    //     categories to the transfer (the business event), not to either
    //     half of its double-entry bookkeeping. @ManyToMany assumes both
    //     ends are entities with identity. Here one end is a grouping UUID.
    //     JPA's @ManyToMany simply does not describe this relationship.
    //
    //  3. AND THE ONE THAT MATTERS MOST: a Set<Transaction> on this class
    //     means "load a category, get every transfer ever tagged with it."
    //     For "allowance" after a year that is hundreds of rows, fetched
    //     because somebody asked for a category NAME. With LAZY it is an
    //     N+1 waiting to happen; with EAGER it is guaranteed.
    //
    //     The general rule, worth keeping past today:
    //     ⚠️ MAP THE ASSOCIATION IN THE DIRECTION YOU ACTUALLY QUERY IT.
    //     You will ask "what categories does this transfer have?" (small,
    //     bounded — two or three). You will never legitimately ask "give me
    //     the Category object with all its transfers attached"; you will
    //     ask "page 1 of transfers in category X", which is a QUERY, not a
    //     field.
    //
    //  So this class stays a plain entity with three fields and NO
    //  collection. The N:N lives in the link table and is reached with
    //  explicit queries — step 2.5c.
    //
    //  Write that decision as a comment here. When your mentor asks "why
    //  no @ManyToMany on a many-to-many?", this comment is the answer, and
    //  it is a better answer than having written one.
    // =====================================================================

    /*
     * Deliberately no @ManyToMany here. Categories are queried from transfers
     * through transfer_categories, and "transfers for a category" is a paged
     * query, not a collection hanging off this entity.
     */

    // ---------------------------------------------------------------------
    //  equals / hashCode — on `name`, not `id`.
    //
    //  Same reasoning as User using email: `id` is what the ROW is, `name`
    //  is what the CATEGORY is. Two unsaved Category("food") objects should
    //  be equal; with id-based equality both have null ids and would
    //  compare equal to every other unsaved category too.
    //
    //  And name never changes identity — if you rename "food" to "meals",
    //  you have arguably made a different category, which is a decision for
    //  a later day and not something a setter should do silently.
    // ---------------------------------------------------------------------
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;

        if (other == null || getClass() != other.getClass()) return false;

        Category category = (Category) other;

        return name.equals(category.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
