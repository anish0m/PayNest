package com.paynest.transfer.model;

/**
 * Transfer lifecycle states.
 *
 * PENDING is included for the Day-17 split architecture. No Day-08 code
 * persists PENDING because the debit, credit, and status update commit in
 * one transaction; it becomes observable only when the transaction splits.
 */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    FAILED
}