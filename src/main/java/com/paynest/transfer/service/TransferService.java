package com.paynest.transfer.service;

import com.paynest.transfer.model.Transfer;
import com.paynest.transfer.repository.TransferRepository;
import com.paynest.transfer.exception.InsufficientFundsException;
import com.paynest.transfer.exception.SelfTransferException;
import com.paynest.transfer.exception.WalletAccessDeniedException;

import com.paynest.wallet.service.WalletService;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.WalletRepository;
import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.exception.WalletFrozenException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Objects;

import java.math.BigDecimal;

@Service
public class TransferService {

    private final WalletRepository wallets;
    private final TransferRepository transfers;
    private final WalletService walletService;

    public TransferService(
            WalletRepository wallets,
            TransferRepository transfers,
            WalletService walletService) {
        this.wallets = wallets;
        this.transfers = transfers;
        this.walletService = walletService;
    }


    // The debit, credit, and transfer record are atomic:
    // either all three are committed, or none are.
    @Transactional(
            propagation = Propagation.REQUIRED,
            rollbackFor = Exception.class)
    public Transfer transfer(
            Long senderWalletId,
            Long receiverWalletId,
            BigDecimal amount) {

        Wallet sender = wallets.findById(senderWalletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(senderWalletId));

        Wallet receiver = wallets.findById(receiverWalletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(receiverWalletId));

        if (Objects.equals(senderWalletId, receiverWalletId)) {
            throw new SelfTransferException(senderWalletId);
        }

        if (!sender.getStatus().allowsMovement()) {
            throw new WalletFrozenException(
                    sender.getId(), sender.getStatus());
        }

        if (!receiver.getStatus().allowsMovement()) {
            throw new WalletFrozenException(
                    receiver.getId(), receiver.getStatus());
        }

        int rows = wallets.debitIfSufficient(senderWalletId, amount);

        if (rows == 0) {
            BigDecimal available = wallets.findById(senderWalletId)
                    .map(Wallet::getBalance)
                    .orElse(BigDecimal.ZERO);

            throw new InsufficientFundsException(
                    senderWalletId, amount, available);
        }

        // receiver is deliberately read a second time, because
        // the receiver loaded is detached by the time the credit runs
        Wallet managedReceiver = wallets.findById(receiverWalletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(receiverWalletId));

        managedReceiver.credit(amount);

        Transfer transfer = new Transfer(
                amount, senderWalletId, receiverWalletId);
        transfer.markCompleted();

        return transfers.save(transfer);
    }
    
    @Transactional(readOnly = true)
    public Page<Transfer> history(
            Long walletId,
            String callerEmail,
            Pageable pageable) {

        Wallet requestedWallet = wallets.findById(walletId)
                .orElseThrow(() ->
                        new WalletNotFoundException(walletId));

        Wallet callerWallet = walletService.getByEmail(callerEmail);

        // Existence is checked first deliberately: this API chooses 404 for an
        // unknown wallet and 403 only when the wallet exists but is not yours.
        if (!Objects.equals(
                requestedWallet.getId(),
                callerWallet.getId())) {
            throw new WalletAccessDeniedException(walletId);
        }

        return transfers.findByWalletId(walletId, pageable);
    }
}
