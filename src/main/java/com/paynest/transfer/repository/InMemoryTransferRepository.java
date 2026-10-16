package com.paynest.transfer.repository;

import com.paynest.transfer.model.Transfer;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("test")
public class InMemoryTransferRepository implements TransferRepository {

    private final Map<UUID, Transfer> transfersById = new ConcurrentHashMap<>();

    private static final Comparator<Transfer> CREATED_AT_DESC =
            Comparator.comparing(
                    Transfer::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder()));

    @Override
    public Transfer save(Transfer transfer) {
        transfersById.put(transfer.getId(), transfer);
        return transfer;
    }

    @Override
    public Optional<Transfer> findById(UUID id) {
        return Optional.ofNullable(transfersById.get(id));
    }

    @Override
    public List<Transfer> findBySenderWalletId(Long walletId) {
        return transfersById.values().stream()
                .filter(transfer -> Objects.equals(
                        transfer.getSenderWalletId(), walletId))
                .sorted(CREATED_AT_DESC)
                .toList();
    }

    @Override
    public List<Transfer> findByReceiverWalletId(Long walletId) {
        return transfersById.values().stream()
                .filter(transfer -> Objects.equals(
                        transfer.getReceiverWalletId(), walletId))
                .sorted(CREATED_AT_DESC)
                .toList();
    }
}
