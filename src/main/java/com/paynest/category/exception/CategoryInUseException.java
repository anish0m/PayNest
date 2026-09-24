package com.paynest.category.exception;

public class CategoryInUseException extends RuntimeException {

    private final String name;
    private final long transferCount;

    public CategoryInUseException(String name, long transferCount) {
        super(transferCount + " transfers still use category: " + name);
        this.name = name;
        this.transferCount = transferCount;
    }

    public String getName() {
        return name;
    }

    public long getTransferCount() {
        return transferCount;
    }
}
