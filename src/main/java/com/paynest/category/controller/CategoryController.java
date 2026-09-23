package com.paynest.category.controller;

import com.paynest.category.dto.CategoryResponse;
import com.paynest.category.dto.CreateCategoryRequest;
import com.paynest.category.dto.TagRequest;
import com.paynest.category.exception.CategoryInUseException;
import com.paynest.category.exception.CategoryNotFoundException;
import com.paynest.category.exception.DuplicateCategoryException;
import com.paynest.category.model.Category;
import com.paynest.category.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// =========================================================================
//  DAY-05, STEP 2.5f — the web edge for categories and tagging.
// =========================================================================
//  @RestController, and constructor-inject CategoryService.
//
//  ⚠️ THIS CLASS NEEDS TWO BASE PATHS, which is why it does NOT get a
//  single class-level @RequestMapping. Put the path on each method.
//
//  The reason is worth thinking about rather than just doing:
//
//      /categories                              -> the category resource
//      /transfers/{transferId}/categories       -> the LINK resource
//
//  The second is a SUB-RESOURCE. "The categories of this transfer" is a
//  thing you can list, add to, and remove from — so it gets its own URL
//  under the transfer that owns it. That is how REST expresses a
//  relationship: not as a verb (/tagTransfer) but as a collection that
//  lives at a path.
//
//  No verbs in any path. The verb is the HTTP method — Day-02's rule.
//
//  (If you prefer, split this into two controllers, CategoryController
//  and TransferCategoryController. Defensible and arguably cleaner. Say
//  which you chose and why.)
// =========================================================================

@RestController
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

// -------------------------------------------------------------------------
//  CATEGORY CRUD
// -------------------------------------------------------------------------
//
//  POST   /categories              @Valid @RequestBody CreateCategoryRequest
//                                  -> 201 + Location: /categories/{name}
//                                  -> 409 if it exists (DuplicateCategory)
//
//  GET    /categories              -> 200, List<CategoryResponse>
//                                     Empty list is 200 [], never 404 —
//                                     the collection exists and is empty.
//
//  GET    /categories/{name}       -> 200 or 404
//
//  PUT    /categories/{name}       @Valid @RequestBody CreateCategoryRequest
//                                  -> 200 with the renamed category
//                                  (reuse the create DTO: it carries
//                                   exactly the one editable field. If that
//                                   feels like a coincidence, it is —
//                                   consider a separate RenameCategoryRequest
//                                   and say which you picked.)
//
//  DELETE /categories/{name}       -> 204, or 404, or 409 IF IN USE
//
//      ⚠️ THE 409 IS THE INTERESTING ONE and the best thing here to
//      demonstrate. Deleting a category that is still tagged to a transfer
//      is refused BY THE DATABASE (the FK from V4), not by your code.
//      Catch DataIntegrityViolationException in the service, rethrow as
//      CategoryInUseException, map it to 409 here.
//
//      409, not 400: the request is perfectly well-formed. The world is
//      just not in the state the caller assumed. Resending an identical
//      400 is pointless; resending this 409 might succeed tomorrow, once
//      the transfers are untagged.
// -------------------------------------------------------------------------


// -------------------------------------------------------------------------
//  THE N:N ENDPOINTS
// -------------------------------------------------------------------------
//
//  GET    /transfers/{transferId}/categories
//         -> 200, List<CategoryResponse>   "what is this transfer tagged?"
//
//  POST   /transfers/{transferId}/categories
//         @Valid @RequestBody TagRequest
//         -> 201 if newly tagged
//         -> 200 if it was already tagged   (see below)
//         -> 404 if the category name does not exist
//
//      ⚠️ THE 201-vs-200 DECISION, and it is a real one.
//      Because tag() uses ON CONFLICT DO NOTHING, calling this twice is
//      SAFE. But "safe" and "the same response" are different claims.
//
//      Returning 201 both times is defensible (the end state is identical
//      — that is what idempotent means). Returning 200 the second time is
//      more informative. Returning 409 would be wrong: nothing conflicted,
//      the database absorbed it by design.
//
//      Pick one. Write the reason in a comment. This is exactly the kind
//      of decision that is invisible in the code and obvious in a review.
//
//  DELETE /transfers/{transferId}/categories/{categoryName}
//         -> 204 if removed, 404 if that tag was not there
//
//      Note this is idempotent in the Day-02 sense: the first call returns
//      204, the second 404, and the SERVER STATE is the same either way.
//      Idempotency is a claim about state, not about the response.
//
//  GET    /categories/summary      -> 200, the per-category counts
//
//      ⚠️ PUT THIS MAPPING **ABOVE** GET /categories/{name}, or Spring may
//      match "summary" as a {name}. Spring's path matching does prefer the
//      more specific literal, but relying on that is fragile, and ordering
//      makes the intent obvious to a human reader too.
//
//      This endpoint is the single best proof the N:N works: its answer
//      cannot be produced without aggregating across the link table.
// -------------------------------------------------------------------------

    // CRUD : create

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CreateCategoryRequest request) {
        Category category = service.create(request.name());

        URI location = URI.create("/categories/" + category.getName());
        return ResponseEntity.created(location)
                .body(CategoryResponse.from(category));
    }

    // CRUD : read

    @GetMapping("/categories")
    public List<CategoryResponse> findAll() {
        return service.findAll()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @GetMapping("/categories/summary")
    public List<Map<String, Object>> summary() {
        return service.summary();
    }

    @GetMapping("/categories/{name}")
    public CategoryResponse findByName(@PathVariable String name) {
        return CategoryResponse.from(service.getByName(name));
    }

    // CRUD : update

    @PutMapping("/categories/{name}")
    public CategoryResponse rename(
            @PathVariable String name,
            @Valid @RequestBody CreateCategoryRequest request) {
        return CategoryResponse.from(service.rename(name, request.name()));
    }

    // CRUD : delete

    @DeleteMapping("/categories/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        service.delete(name);
        return ResponseEntity.noContent().build();
    }

// -------------------------------------------------------------------------
//  EXCEPTION HANDLERS
// -------------------------------------------------------------------------
//  You need CategoryNotFoundException -> 404,
//           DuplicateCategoryException -> 409,
//           CategoryInUseException     -> 409.
//
//  ⚠️ DO NOT COPY UserController's THREE HANDLERS INTO THIS FILE.
//  That is the moment the duplication becomes real — and it is precisely
//  the moment CONTEXT.md predicted when it said the move to
//  @RestControllerAdvice should wait until it was "a genuine, visible
//  improvement rather than architecture applied in advance."
//
//  This second controller IS that moment. Two options:
//
//    a) write these three handlers here for now, and let step 8
//       (GlobalExceptionHandler) pull ALL of them out; or
//    b) do step 8 first, and put every handler there from the start.
//
//  (b) is less total work and avoids writing code you will delete.
//  (a) lets you SEE the duplication before removing it, which is the
//  better lesson and the reason the Day-02 comment deferred it.
//
//  Your call — but if you pick (a), do not skip step 8 afterwards.
// -------------------------------------------------------------------------


    @GetMapping("/transfers/{transferId}/categories")
    public List<CategoryResponse> categoriesFor(@PathVariable UUID transferId) {
        return service.categoriesFor(transferId)
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @PostMapping("/transfers/{transferId}/categories")
    public ResponseEntity<Void> tag(
            @PathVariable UUID transferId,
            @Valid @RequestBody TagRequest request) {
        boolean created = service.tag(transferId, request.categoryName());

        URI location = URI.create(
                "/transfers/" + transferId + "/categories/" + request.categoryName());

        // 201 means this request created the link; 200 means it was already present.
        return created
                ? ResponseEntity.created(location).build()
                : ResponseEntity.ok().build();
    }

    @DeleteMapping("/transfers/{transferId}/categories/{categoryName}")
    public ResponseEntity<Void> untag(
            @PathVariable UUID transferId,
            @PathVariable String categoryName) {
        boolean removed = service.untag(transferId, categoryName);

        return removed
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(CategoryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateCategoryException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<Map<String, Object>> handleInUse(CategoryInUseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", e.getMessage(),
                        "name", e.getName(),
                        "transferCount", e.getTransferCount()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();

        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(errors);
    }

// =========================================================================
//  THE DEMO — what to actually run for your mentor.
// =========================================================================
//  T=$(uuidgen)     # stands in for a real transfer until Day-08
//
//  1.  curl localhost:8090/categories
//         -> the seeded categories from V4
//
//  2.  curl -X POST localhost:8090/categories \
//        -H 'Content-Type: application/json' -d '{"name":"skincare"}'
//         -> 201 + Location
//
//  3.  curl -X POST localhost:8090/categories \
//        -H 'Content-Type: application/json' -d '{"name":"skincare"}'
//         -> 409. The UNIQUE constraint, not an if-statement.
//
//  4.  ONE TRANSFER, TWO CATEGORIES — the N:N, left to right:
//      curl -X POST localhost:8090/transfers/$T/categories \
//        -H 'Content-Type: application/json' -d '{"categoryName":"allowance"}'
//      curl -X POST localhost:8090/transfers/$T/categories \
//        -H 'Content-Type: application/json' -d '{"categoryName":"food"}'
//      curl localhost:8090/transfers/$T/categories
//         -> BOTH categories. One transfer, many categories.
//
//  5.  MANY TRANSFERS, ONE CATEGORY — right to left:
//      repeat step 4's first command with two more UUIDs, then
//      curl localhost:8090/categories/summary
//         -> allowance: 3. One category, many transfers.
//
//      ⚠️ STEPS 4 AND 5 TOGETHER ARE THE WHOLE POINT. Either one alone
//      only proves 1:N. It is both directions being "many" that makes it
//      N:N, and that is the thing to say out loud while showing it.
//
//  6.  IDEMPOTENCY — run step 4's first command again
//         -> no duplicate, no error. The composite PK absorbed it.
//
//  7.  REFERENTIAL INTEGRITY — the best one:
//      curl -i -X DELETE localhost:8090/categories/allowance
//         -> 409. The database refuses to orphan three tagged transfers.
//      Then untag them and try again -> 204.
//
//  8.  AND IN psql, so it is not just your own API agreeing with itself:
//      SELECT tc.transfer_id, c.name
//        FROM transfer_categories tc
//        JOIN categories c ON c.id = tc.category_id
//       ORDER BY tc.transfer_id;
// =========================================================================
}