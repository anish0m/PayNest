package com.paynest.transfer.dto;

import com.paynest.transfer.model.Transfer;
import com.paynest.transfer.model.TransferStatus;
import com.paynest.transfer.model.TransferDirection;

import java.util.Objects;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        Long senderWalletId,
        Long receiverWalletId,
        String amount,
        TransferStatus status,
        Instant createdAt,
        Instant completedAt,
        TransferDirection direction) {

    public static TransferResponse from(
            Transfer transfer,
            Long viewerWalletId) {

        TransferDirection direction = Objects.equals(
                transfer.getSenderWalletId(),
                viewerWalletId)
                ? TransferDirection.SENT
                : TransferDirection.RECEIVED;

        return new TransferResponse(
                transfer.getId(),
                transfer.getSenderWalletId(),
                transfer.getReceiverWalletId(),
                transfer.getAmount().toPlainString(),
                transfer.getStatus(),
                transfer.getCreatedAt(),
                transfer.getCompletedAt(),
                direction);
    }
}