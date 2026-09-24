package com.paynest.category.exception;

public class CategoryNotFoundException extends RuntimeException {

    private final String name;

    public CategoryNotFoundException(String name) {
        super("No category found with name: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
