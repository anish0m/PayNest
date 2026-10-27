package com.paynest.transfer.repository;

import com.paynest.transfer.model.Transfer;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

@Repository
@Profile({"jpa", "jdbc"})
public class JpaTransferRepository implements TransferRepository {

    private final SpringDataTransferRepository transfers;
    private final EntityManager entityManager;

    public JpaTransferRepository(SpringDataTransferRepository transfers,
                                 EntityManager entityManager) {
        this.transfers = transfers;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Transfer save(Transfer transfer) {
        Transfer saved = transfers.save(transfer);

        transfers.flush();
        entityManager.refresh(saved);

        // returns createdAt, so refresh the entity after the database fills it.
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transfer> findById(UUID id) {
        return transfers.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transfer> findBySenderWalletId(Long walletId) {
        return transfers.findBySenderWalletIdOrderByCreatedAtDesc(walletId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transfer> findByReceiverWalletId(Long walletId) {
        return transfers.findByReceiverWalletIdOrderByCreatedAtDesc(walletId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Transfer> findByWalletId(Long walletId, Pageable pageable) {
        return transfers.findByWalletId(walletId, pageable);
    }
}
