package com.paynest.transfer.repository;

import com.paynest.transfer.model.Transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataTransferRepository extends JpaRepository<Transfer, UUID> {

    List<Transfer> findBySenderWalletIdOrderByCreatedAtDesc(Long walletId);

    List<Transfer> findByReceiverWalletIdOrderByCreatedAtDesc(Long walletId);
}
