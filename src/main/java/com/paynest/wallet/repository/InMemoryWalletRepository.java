package com.paynest.wallet.repository;

import com.paynest.wallet.model.Wallet;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Field;

import java.math.BigDecimal;

import java.util.Objects;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
@Profile("test")
public class InMemoryWalletRepository implements WalletRepository {

    // One source of truth; scanning is fast enough for unit-test data.
    private final Map<Long, Wallet> walletsById = new ConcurrentHashMap<>();

    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Wallet save(Wallet wallet) {
        if (wallet.getId() == null) {
            assignId(wallet, nextId.getAndIncrement());
        }

        walletsById.put(wallet.getId(), wallet);
        return wallet;
    }

    private void assignId(Wallet wallet, Long id) {
        try {
            Field field = Wallet.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(wallet, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not assign wallet id", e);
        }
    }

    @Override
    public Optional<Wallet> findByUserId(Long userId) {
        return walletsById.values().stream()
                .filter(wallet -> wallet.getUser() != null
                        && Objects.equals(wallet.getUser().getId(), userId))
                .findFirst();
    }

    @Override
    public Optional<Wallet> findById(Long id) {
        return Optional.ofNullable(walletsById.get(id));
    }

    @Override
    public boolean existsByUserId(Long userId) {
        return findByUserId(userId).isPresent();
    }

    // This in-memory fake cannot model JPA detachment;
    // the receiver re-read is verified against PostgreSQL.
    @Override
    public int debitIfSufficient(Long walletId, BigDecimal amount) {
        Wallet wallet = walletsById.get(walletId);

        if (wallet == null || wallet.getBalance().compareTo(amount) < 0) {
            return 0;
        }

        wallet.debit(amount);
        return 1;
    }

}
