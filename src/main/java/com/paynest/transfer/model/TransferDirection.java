package com.paynest.transfer.model;

/**
 * The direction of a transfer as seen by one viewer.
 *
 * Direction is computed when building a response, not persisted: the same
 * transfer is SENT to its sender and RECEIVED by its receiver.
 */
public enum TransferDirection {
    SENT,
    RECEIVED
}
