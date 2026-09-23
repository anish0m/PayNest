package com.paynest.category.dto;

// =========================================================================
//  DAY-05, STEP 2.5e(i) — CategoryResponse
// =========================================================================
//  A record: what PayNest sends back when asked about a category.
//
//      public record CategoryResponse(Long id, String name) { }
//
//  Plus a static factory:  from(Category c)
//
//  WHY id IS HERE but was excluded from CreateUserRequest: direction.
//  A REQUEST must not let the caller choose an id (mass assignment).
//  A RESPONSE telling the caller the id the database assigned is the
//  whole point — it is how they refer to it next time.
//
//  Same class, opposite rules, because the arrow points the other way.
//
//  createdAt deliberately omitted — nobody asked for it, and a DTO is
//  shaped by what the client needs, not by what the table holds.

import com.paynest.category.model.Category;

public record CategoryResponse(Long id, String name) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName());
    }
}
