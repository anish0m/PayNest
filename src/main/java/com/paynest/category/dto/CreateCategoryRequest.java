package com.paynest.category.dto;

// =========================================================================
//  DAY-05, STEP 2.5e(ii) — CreateCategoryRequest
// =========================================================================
//  public record CreateCategoryRequest(String name) { }
//
//  One field. No id, no createdAt — the database owns both, so the type
//  must not be able to express them. Same move as CreateUserRequest.
//
//  ADD THE SAME CONSTRAINTS YOU ARE ADDING IN STEP 3:
//      @NotBlank(message = "...")
//      @Size(max = 50, message = "...")   <- mirrors V4's VARCHAR(50)
//
//  (imports from jakarta.validation.constraints)
//
//  ⚠️ IF YOU DO STEP 2.5 BEFORE STEP 3, these annotations will do NOTHING
//  until @Valid is on the controller parameter — the starter is on the
//  classpath from step 1, but nothing runs the constraints without @Valid.
//  Present but not participating, and now you can predict it rather than
//  discover it.

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(max = 50, message = "Category name must be at most 50 characters")
        String name) {
}