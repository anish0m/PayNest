package com.paynest.wallet.repository;

import com.paynest.wallet.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface SpringDataWalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Modifying(clearAutomatically = true,
            flushAutomatically = true)
    @Query("""
           UPDATE Wallet w
              SET w.balance = w.balance - :amount
            WHERE w.id = :walletId
              AND w.balance >= :amount
           """)
    int debitIfSufficient(@Param("walletId") Long walletId,
                          @Param("amount") BigDecimal amount);
}
