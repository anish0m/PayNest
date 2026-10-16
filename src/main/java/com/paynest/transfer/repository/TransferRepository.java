package com.paynest.transfer.repository;

import com.paynest.transfer.model.Transfer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {

    Transfer save(Transfer transfer);

    Optional<Transfer> findById(UUID id);

    List<Transfer> findBySenderWalletId(Long walletId);

    List<Transfer> findByReceiverWalletId(Long walletId);
}
