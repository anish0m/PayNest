package com.paynest.transfer.dto;

import com.paynest.transfer.model.Transfer;
import com.paynest.transfer.model.TransferStatus;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        Long senderWalletId,
        Long receiverWalletId,
        String amount,
        TransferStatus status,
        Instant createdAt,
        Instant completedAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getSenderWalletId(),
                transfer.getReceiverWalletId(),
                transfer.getAmount().toPlainString(),
                transfer.getStatus(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt());
    }
}