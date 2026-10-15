package com.paynest.wallet.repository;

import com.paynest.user.model.User;
import com.paynest.wallet.repository.SpringDataWalletRepository;
import com.paynest.user.repository.UserRepository;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.model.WalletStatus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles({"jpa", "jpa-it"})
@Transactional
class WalletRepositoryIT {

    @Autowired
    private SpringDataWalletRepository wallets;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM wallets").update();
        jdbc.sql("DELETE FROM users").update();
    }

    private static User user(String email) {
        return new User("Anishom", "Frost", email, "Pass1234#");
    }

    @Test
    void savedWalletIsFoundByUserId() {
        User user = users.save(user("khi0ne@example.com"));
        Wallet saved = wallets.saveAndFlush(new Wallet(user));

        Wallet found = wallets.findByUserId(user.getId()).orElseThrow();

        assertEquals(saved.getId(), found.getId());
    }

    @Test
    void findByUserIdIsEmptyForAUserWithoutAWallet() {
        User user = users.save(user("khi0ne@example.com"));

        assertTrue(wallets.findByUserId(user.getId()).isEmpty());
    }

    @Test
    void existsByUserIdMatchesFindByUserId() {
        User withWallet = users.save(user("khi0ne@example.com"));
        User withoutWallet = users.save(user("rauf00n@example.com"));

        wallets.saveAndFlush(new Wallet(withWallet));

        assertTrue(wallets.existsByUserId(withWallet.getId()));
        assertTrue(wallets.findByUserId(withWallet.getId()).isPresent());
        assertTrue(wallets.findByUserId(withoutWallet.getId()).isEmpty());
    }

    @Test
    void defaultsComeFromTheDatabase() {
        User user = users.save(user("khi0ne@example.com"));

        jdbc.sql("INSERT INTO wallets (user_id) VALUES (?)")
                .param(user.getId())
                .update();

        Wallet wallet = wallets.findByUserId(user.getId()).orElseThrow();

        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("0.0000")));
        assertEquals(WalletStatus.ACTIVE, wallet.getStatus());
        assertNotNull(wallet.getCreatedAt());
    }

    @Test
    void secondWalletForTheSameUserIsRejected() {
        User user = users.save(user("khi0ne@example.com"));

        wallets.saveAndFlush(new Wallet(user));

        assertThrows(DataIntegrityViolationException.class,
                () -> wallets.saveAndFlush(new Wallet(user)));
    }

    @Test
    void negativeBalanceIsRejected() {
        User user = users.save(user("khi0ne@example.com"));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.sql("INSERT INTO wallets (user_id, balance, status) VALUES (?, -1, 'ACTIVE')")
                        .param(user.getId())
                        .update());
    }

    @Test
    void invalidStatusIsRejected() {
        User user = users.save(user("khi0ne@example.com"));
        Wallet wallet = wallets.saveAndFlush(new Wallet(user));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.sql("UPDATE wallets SET status = 'frozen' WHERE id = ?")
                        .param(wallet.getId())
                        .update());
    }

    @Test
    void walletForAMissingUserIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.sql("INSERT INTO wallets (user_id, balance, status) VALUES (999999, 0, 'ACTIVE')")
                        .update());
    }
}