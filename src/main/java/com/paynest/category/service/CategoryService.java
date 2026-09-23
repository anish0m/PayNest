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

// =========================================================================
//  DAY-05, STEP 2.5d — CategoryService.
// =========================================================================
//  @Service, constructor injection, two final fields:
//      SpringDataCategoryRepository categories
//      TransferCategoryRepository   links
//
//  No @Autowired (single constructor). Same as UserService.
//
//  ⚠️ NOTE THIS SERVICE TALKS TO TWO REPOSITORIES, and that is the first
//  time in PayNest. It is fine, and it is the reason the service layer
//  exists: "tag a transfer" needs the category to be looked up AND the
//  link to be written, and deciding that those two belong together is a
//  business decision, not a storage one. Neither repository could own it.
// =========================================================================

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
// -------------------------------------------------------------------------
//  CRUD — the four you already know
// -------------------------------------------------------------------------
//
//  create(String name) -> Category
//      Look up by name first; if present, throw DuplicateCategoryException.
//      Then categories.save(new Category(name)).
//
//      ⚠️ SAME LOOK-THEN-ACT GAP AS register(). Two concurrent requests
//      both find nothing and both insert; the UNIQUE constraint in V4 is
//      what actually prevents the duplicate. The check here buys a clean
//      error message, NOT safety. Fourth appearance of policy vs
//      guarantee — you should be able to say this one in your sleep by now,
//      and being able to say it out loud is worth more than the code.
//
//  findAll() -> Collection<Category>       categories.findAll()
//  findByName(String) -> Optional<Category>
//  getByName(String) -> Category           throws CategoryNotFoundException
//
//      Optional for "is this taken?", throwing for "load the one I named".
//      Absence is not always an error — slice 4's rule, still holding.
//
//  rename(String oldName, String newName) -> Category
//      @Transactional. getByName, then setName. NO save() call under JPA —
//      dirty checking already scheduled the UPDATE. Write a comment saying
//      so; it is the thing that looks like a bug and is not.
//
//  delete(String name)
//      categories.deleteByName(name); throw if 0 rows deleted.
//
//      ⚠️ AND HERE YOU WILL MEET THE FOREIGN KEY, DELIBERATELY.
//      Delete a category that is still tagged to a transfer and Postgres
//      refuses: "update or delete on table categories violates foreign key
//      constraint on table transfer_categories".
//
//      That is DataIntegrityViolationException in Spring terms. DO NOT add
//      ON DELETE CASCADE to make it go away — cascading here would
//      silently erase the fact that twelve transfers were allowances.
//      Catch it and throw something meaningful
//      (CategoryInUseException -> 409 Conflict), because the world is not
//      in the state the caller assumed. Exactly the 409-vs-400 line:
//      the request is fine, the world isn't.
//
//      THIS IS A GOOD DEMO. Tag a transfer, try to delete the category,
//      watch the database refuse. Referential integrity, visible.
// -------------------------------------------------------------------------

    // CRUD : create

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

    // CRUD : read

    public Collection<Category> findAll() {
        return categories.findAll();
    }

    public Optional<Category> findByName(String name) {
        return categories.findByName(name);
    }

    public Category getByName(String name) {
        return categories.findByName(name)
                .orElseThrow(() -> new CategoryNotFoundException(name));
    }

    // CRUD : update

    @Transactional
    public Category rename(String oldName, String newName) {
        if (categories.findByName(newName).isPresent()) {
            throw new DuplicateCategoryException(newName);
        }

        Category category = getByName(oldName);
        category.setName(newName);

        return category;
    }

    // CRUD : delete

    @Transactional
    public void delete(String name) {
        Category category = getByName(name);
        long transferCount = links.findTransfersForCategory(category.getId()).size();

        try {
            long deleted = categories.deleteByName(name);

            if (deleted == 0) {
                throw new CategoryNotFoundException(name);
            }

            categories.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CategoryInUseException(name, transferCount);
        }
    }

    // -------------------------------------------------------------------------
//  THE N:N OPERATIONS
// -------------------------------------------------------------------------
//
//  tag(UUID transferId, String categoryName) -> boolean
//      getByName(categoryName)  — 404 if the category does not exist,
//                                 which is right: you cannot tag with
//                                 something that is not a category
//      then links.tag(transferId, category.getId())
//
//      Returns true if newly tagged, false if it already was. Because of
//      ON CONFLICT DO NOTHING, calling this twice is safe — say so in a
//      comment, since idempotency is the property a reader will not assume.
//
//  untag(UUID transferId, String categoryName) -> boolean
//
//  categoriesFor(UUID transferId) -> List<Category>
//  transfersIn(String categoryName) -> List<UUID>
//
//      Note the asymmetry and why it is honest: one returns entities, the
//      other returns UUIDs, because Transaction is not an entity until
//      Day-08. Do not invent a wrapper class to make them look symmetric.
//
//  summary() -> the per-category counts
//
//  ⚠️ NOT VALIDATED HERE: that transferId refers to a real transfer.
//  It cannot be, today — Option A in V4 means there is no FK on
//  transfer_id, and there is no transfers table to check against. So this
//  service will happily tag a UUID that corresponds to nothing.
//
//  WRITE THAT DOWN AS A COMMENT. It is a known, dated limitation
//  (closes on Day-08), not an oversight — and the difference between those
//  two is the entire point of how this project records debt.
// -------------------------------------------------------------------------
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

    public List<UUID> transfersIn(String categoryName) {
        Category category = getByName(categoryName);
        return links.findTransfersForCategory(category.getId());
    }

    public List<Map<String, Object>> summary() {
        return links.countByCategory();
    }
}