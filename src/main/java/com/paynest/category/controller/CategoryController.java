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

@RestController
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

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

}