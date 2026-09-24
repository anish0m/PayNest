package com.paynest.category.dto;

import jakarta.validation.constraints.NotBlank;

public record TagRequest(

        @NotBlank(message = "Category name is required")
        String categoryName) {
}