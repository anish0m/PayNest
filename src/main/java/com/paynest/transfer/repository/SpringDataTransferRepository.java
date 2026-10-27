package com.paynest.transfer.repository;

import com.paynest.transfer.model.Transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SpringDataTransferRepository extends JpaRepository<Transfer, UUID> {

    List<Transfer> findBySenderWalletIdOrderByCreatedAtDesc(Long walletId);

    List<Transfer> findByReceiverWalletIdOrderByCreatedAtDesc(Long walletId);

    @Query("""
       SELECT t
       FROM Transfer t
       WHERE t.senderWalletId = ?1
          OR t.receiverWalletId = ?1
       ORDER BY t.createdAt DESC
       """)
    Page<Transfer> findByWalletId(Long walletId, Pageable pageable);
}
