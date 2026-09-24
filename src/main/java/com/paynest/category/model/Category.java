package com.paynest.category.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "categories")
@Getter
@ToString
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
    }

    public Category(String name) {
        setName(name);
    }

    Category(Long id, String name, Instant createdAt) {
        this(name);
        this.id = id;
        this.createdAt = createdAt;
    }

    /**
     * Rebuilds a Category that already exists in storage.
     *
     * <p>Only repositories should call this. Named {@code fromRow} rather than
     * exposing the constructor, so that calling it from a controller looks
     * obviously wrong to a reader — a controller must never invent an id.
     */
    public static Category fromRow(Long id, String name, Instant createdAt) {
        return new Category(id, name, createdAt);
    }

    /**
     * Normalises with {@code trim().toLowerCase()} before storing.
     *
     * <p>"Food", "food" and " food " are one category to a human and three rows
     * to a UNIQUE constraint. Normalising here means the constraint enforces
     * what is meant rather than what was typed.
     *
     * <p>V5 also adds {@code CHECK (name = lower(trim(name)))} so the rule is
     * enforced for writers that never pass through this setter — V4's seed data
     * was exactly such a writer, and it was capitalised.
     */
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name cannot be null or blank");
        }

        this.name = name.trim().toLowerCase(Locale.ROOT);
    }

    /*
     * Deliberately no @ManyToMany here. Categories are queried from transfers
     * through transfer_categories, and "transfers for a category" is a paged
     * query, not a collection hanging off this entity.
     */

    /**
     * Equality on {@code name}, not {@code id} — {@code id} is what the ROW is,
     * {@code name} is what the CATEGORY is. Two unsaved Category("food")
     * objects should be equal; with id-based equality both have null ids and
     * would compare equal to every other unsaved category too.
     */
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
