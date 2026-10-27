package com.paynest.transfer.repository;

import com.paynest.transfer.model.Transfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(UUID id);

    List<Transfer> findBySenderWalletId(Long walletId);

    List<Transfer> findByReceiverWalletId(Long walletId);

    /**
     * Returns the wallet's transfers across both sender and receiver sides,
     * ordered by createdAt descending and fetched using offset pagination.
     *
     * <p>Because this uses offset pagination, concurrent inserts may cause
     * duplicate or skipped rows between page requests.</p>
     */
    Page<Transfer> findByWalletId(Long walletId, Pageable pageable);
}
