package com.paynest.transfer.repository;

import com.paynest.transfer.model.Transfer;
import com.paynest.transfer.model.TransferStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataTransferRepository extends JpaRepository<Transfer, UUID> {

    // measured against 100,003 transfers in paynest.
    // The history OR uses BitmapOr with both wallet/created_at indexes.
    // Bitmap Heap Scan loses index order, so PostgreSQL uses a top-N Sort for createdAt DESC.
    // A single sender branch uses an ordered Index Scan with no Sort (0.077 ms vs 9.606 ms).
    // UNION ALL was considered but not adopted: both indexes are already used and the Sort stayed in memory.
    @Query("""
       SELECT t
       FROM Transfer t
       WHERE (t.senderWalletId = :walletId
           OR t.receiverWalletId = :walletId)
         AND (:status IS NULL OR t.status = :status)
         AND (CAST(:from AS timestamp) IS NULL OR t.createdAt >= :from)
         AND (CAST(:to AS timestamp) IS NULL OR t.createdAt < :to)
         AND (CAST(:minAmount AS big_decimal) IS NULL OR t.amount >= :minAmount)
         AND (CAST(:maxAmount AS big_decimal) IS NULL OR t.amount <= :maxAmount)
       ORDER BY t.createdAt DESC
       """)
    Page<Transfer> findByWalletId(
            @Param("walletId") Long walletId,
            @Param("status") TransferStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("minAmount") BigDecimal minAmount,
            @Param("maxAmount") BigDecimal maxAmount,
            Pageable pageable);
}
