package com.paynest.wallet.service;

import com.paynest.user.model.User;
import com.paynest.user.repository.UserRepository;
import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final UserRepository users;

    public WalletService(WalletRepository wallets, UserRepository users) {
        this.wallets = wallets;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Wallet getByEmail(String email) {
        User user = users.getByEmail(email);

        return wallets.findByUserId(user.getId())
                .orElseThrow(() -> new WalletNotFoundException(email));
    }
}