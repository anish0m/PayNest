package com.paynest.category.repository;

// =========================================================================
//  DAY-05, STEP 2.5c(i) — Spring Data repository for Category.
// =========================================================================
//  Imports: com.paynest.category.model.Category,
//           org.springframework.data.jpa.repository.JpaRepository,
//           java.util.Optional
//
//  Exactly the shape of SpringDataUserRepository: an INTERFACE with no
//  implementation. Spring Data writes the class at startup by PARSING THE
//  METHOD NAMES.
//
//  Declare:
//      public interface SpringDataCategoryRepository
//              extends JpaRepository<Category, Long>
//
//  <Category, Long> = the entity, and the type of its @Id.
//
//  Then three derived queries:
//
//      Optional<Category> findByName(String name);
//      boolean existsByName(String name);
//      long deleteByName(String name);
//
//  ⚠️ THE NAMES ARE THE API, AND NOTHING CHECKS THE MEANING.
//  findByNmae(...) fails at STARTUP — Spring Data cannot find a property
//  called `nmae` and refuses to build the bean. Good: loud and early.
//
//  But findByCreatedAt would parse PERFECTLY and return the wrong thing.
//  The framework checks that the property EXISTS, never that you meant it.
//  Day-04's throughline, third sighting: the compiler checks types, the
//  framework checks names, the TEST is still the only thing that checks
//  the thing itself.
// =========================================================================

import com.paynest.category.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataCategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    long deleteByName(String name);
}