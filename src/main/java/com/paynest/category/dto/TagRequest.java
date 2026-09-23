package com.paynest.category.dto;

// =========================================================================
//  DAY-05, STEP 2.5e(iii) — TagRequest
// =========================================================================
//  The body for "tag this transfer with this category".
//
//      public record TagRequest(String categoryName) { }
//
//  @NotBlank on it.
//
//  WHY NOT categoryId? Because a caller tagging a transfer knows the word
//  "allowance", not the number 3. Ids are the database's identity; names
//  are the domain's. Letting the API speak in names means the client
//  never has to fetch the category list just to tag something.
//
//  WHY NOT put transferId in this record? It comes from the PATH
//  (/transfers/{transferId}/categories) — the path says WHICH resource,
//  the body says what to do to it. Your Day-02 rule, unchanged.
//
//  A body that also carried a transferId would raise the question of
//  which one wins when they disagree, and every answer to that question
//  is a bug in some reading.

import jakarta.validation.constraints.NotBlank;

public record TagRequest(

        @NotBlank(message = "Category name is required")
        String categoryName) {
}