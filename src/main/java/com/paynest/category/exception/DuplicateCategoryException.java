package com.paynest.category.exception;

public class DuplicateCategoryException extends RuntimeException {

    private final String name;

    public DuplicateCategoryException(String name) {
        super("A category already exists with name: " + name);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
