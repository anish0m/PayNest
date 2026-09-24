package com.paynest.category.service;

import com.paynest.category.exception.CategoryInUseException;
import com.paynest.category.exception.CategoryNotFoundException;
import com.paynest.category.exception.DuplicateCategoryException;
import com.paynest.category.model.Category;
import com.paynest.category.repository.SpringDataCategoryRepository;
import com.paynest.category.repository.TransferCategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Business rules for categories and for tagging transfers.
 *
 * <p>The first service in PayNest to depend on two repositories. That is what
 * the service layer is for: "tag a transfer" needs the category looked up AND
 * the link written, and deciding those two belong together is a business
 * decision, not a storage one. Neither repository could own it.
 *
 * <p><b>Known limitation, closes on Day-08:</b> nothing here validates that a
 * {@code transferId} refers to a real transfer. It cannot — V4 chose Option A
 * (no FK on {@code transfer_id}) because {@code transactions.transfer_id} is
 * deliberately not unique, and there is no {@code transfers} table to point at
 * yet. So this service will happily tag a UUID that corresponds to nothing.
 */
@Service
public class CategoryService {

    private final SpringDataCategoryRepository categories;
    private final TransferCategoryRepository links;

    public CategoryService(
            SpringDataCategoryRepository categories,
            TransferCategoryRepository links
    ) {
        this.categories = categories;
        this.links = links;
    }

    /**
     * Creates a category.
     *
     * <p>The lookup is a look-then-act and loses the same race as
     * {@code UserService.register}: two concurrent requests both find nothing
     * and both insert. The UNIQUE constraint in V4 is what actually prevents the
     * duplicate — this check buys a clean error message, not safety. The catch
     * below is what turns the constraint's refusal into the same exception, so
     * the caller sees one answer either way.
     */
    public Category create(String name) {
        if (categories.findByName(name).isPresent()) {
            throw new DuplicateCategoryException(name);
        }

        try {
            return categories.save(new Category(name));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryException(name);
        }
    }

    public Collection<Category> findAll() {
        return categories.findAll();
    }

    /** {@code Optional} because absence is a normal answer to "is this taken?". */
    public Optional<Category> findByName(String name) {
        return categories.findByName(name);
    }

    /** Throws, because a caller naming a specific category cannot continue without it. */
    public Category getByName(String name) {
        return categories.findByName(name)
                .orElseThrow(() -> new CategoryNotFoundException(name));
    }

    /**
     * Renames a category.
     *
     * <p>No {@code save()} call: inside this transaction the entity is managed,
     * so {@code setName} has already scheduled the UPDATE through dirty
     * checking. The absence of a write is the thing that looks like a bug and is
     * not.
     */
    @Transactional
    public Category rename(String oldName, String newName) {
        if (categories.findByName(newName).isPresent()) {
            throw new DuplicateCategoryException(newName);
        }

        Category category = getByName(oldName);
        category.setName(newName);

        return category;
    }

    /**
     * Deletes a category, unless transfers still use it.
     *
     * <p>{@code getByName} above already throws if the category is missing, so by
     * the time {@code deleteByName} runs the row exists. The only remaining
     * failure is the FOREIGN KEY from V4 refusing to orphan tagged transfers —
     * which is the database enforcing the rule, not this method.
     *
     * <p><b>The {@code flush()} is load-bearing.</b> Without it the DELETE stays
     * queued until commit, the FK violation surfaces after this method has
     * returned, and the catch below never runs.
     *
     * <p><b>⚠️ The count MUST be taken before the try, not inside the catch.</b>
     * Once the FK violation fires, PostgreSQL aborts the whole transaction —
     * SQL state 25P02, <em>"current transaction is aborted, commands ignored
     * until end of transaction block"</em> — so any query in the catch block
     * fails with an unrelated error and the caller gets a 500 instead of a 409.
     *
     * <p>Third appearance of this rule: Day-03 met 25P02 directly, Day-04's
     * persona slice hit it when {@code save()}'s catch called
     * {@code existsByEmail}. <b>The transaction is already dead — read the
     * exception, do not ask it a question.</b>
     *
     * <p>So one extra {@code COUNT(*)} is paid on every delete. That is the
     * honest price of being able to report the refusal, and a count is cheaper
     * than loading every UUID to call {@code size()} on it.
     *
     * <p>Deliberately no {@code ON DELETE CASCADE}: cascading here would
     * silently erase the fact that twelve transfers were allowances.
     */
    @Transactional
    public void delete(String name) {
        Category category = getByName(name);
        long transferCount = links.countTransfersForCategory(category.getId());

        try {
            categories.deleteByName(name);
            categories.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CategoryInUseException(name, transferCount);
        }
    }

    /**
     * Tags a transfer. Safe to call repeatedly — the link table absorbs repeats.
     *
     * @return true if this call created the link, false if it was already there
     */
    public boolean tag(UUID transferId, String categoryName) {
        Category category = getByName(categoryName);
        return links.tag(transferId, category.getId());
    }

    public boolean untag(UUID transferId, String categoryName) {
        Category category = getByName(categoryName);
        return links.untag(transferId, category.getId());
    }

    public List<Category> categoriesFor(UUID transferId) {
        return links.findCategoriesForTransfer(transferId);
    }

    /**
     * The asymmetry with {@link #categoriesFor} is honest, not an oversight: one
     * returns entities, the other UUIDs, because Transaction is not an entity
     * until Day-08.
     */
    public List<UUID> transfersIn(String categoryName) {
        Category category = getByName(categoryName);
        return links.findTransfersForCategory(category.getId());
    }

    public List<Map<String, Object>> summary() {
        return links.countByCategory();
    }
}
