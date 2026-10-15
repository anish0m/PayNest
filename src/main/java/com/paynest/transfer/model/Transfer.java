package com.paynest.transfer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A requested transfer between two wallets.
 *
 * Wallet references are intentionally stored as raw IDs rather than JPA
 * associations. This keeps the transfer-to-wallet boundary narrow and allows
 * the model to survive the Day-17 service split.
 */
@Entity
@Table(name = "transfers")
@Getter
@ToString
public class Transfer {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "sender_wallet_id", nullable = false, updatable = false)
    private Long senderWalletId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "receiver_wallet_id", nullable = false, updatable = false)
    private Long receiverWalletId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transfer() {
    }

    public Transfer(
            BigDecimal amount,
            Long senderWalletId,
            Long receiverWalletId
    ) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        this.id = UUID.randomUUID();
        this.amount = amount;
        this.senderWalletId = senderWalletId;
        this.receiverWalletId = receiverWalletId;
        this.status = TransferStatus.PENDING;
    }

    public void markCompleted() {
        ensureNotTerminal();

        this.status = TransferStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed() {
        ensureNotTerminal();

        this.status = TransferStatus.FAILED;
    }

    private void ensureNotTerminal() {
        if (status == TransferStatus.COMPLETED || status == TransferStatus.FAILED) {
            throw new IllegalStateException(
                    "Terminal transfer status cannot be changed"
            );
        }
    }
}
