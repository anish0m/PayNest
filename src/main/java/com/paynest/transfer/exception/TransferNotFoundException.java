package com.paynest.transfer.exception;

import java.util.UUID;

public final class TransferNotFoundException extends RuntimeException {

    private final UUID id;

    public TransferNotFoundException(UUID id) {
        super("Transfer not found: " + id);
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
