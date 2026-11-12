package com.paynest.transfer.service;

import com.paynest.transfer.dto.TransferFilter;
import com.paynest.transfer.exception.InsufficientFundsException;
import com.paynest.transfer.exception.SelfTransferException;
import com.paynest.transfer.exception.WalletAccessDeniedException;
import com.paynest.transfer.exception.TransferNotFoundException;
import com.paynest.transfer.model.Transfer;
import com.paynest.transfer.repository.TransferRepository;

import com.paynest.wallet.exception.WalletNotFoundException;
import com.paynest.wallet.exception.WalletFrozenException;
import com.paynest.wallet.model.Wallet;
import com.paynest.wallet.repository.WalletRepository;
import com.paynest.wallet.service.WalletService;

import java.math.BigDecimal;

import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class TransferService {

    private static final Logger logger = LoggerFactory.getLogger(TransferService.class);

    private final WalletRepository wallets;
    private final TransferRepository transfers;
    private final WalletService walletService;

    private static final int MAX_TRANSFER_ATTEMPTS = 3;

    private TransferService self;

    public TransferService(
            WalletRepository wallets,
            TransferRepository transfers,
            WalletService walletService) {
        this.wallets = wallets;
        this.transfers = transfers;
        this.walletService = walletService;
        this.self = this;
    }

    @Autowired(required = false)
    public void setSelf(@Lazy TransferService self) {
        this.self = self;
    }

    public Transfer transfer(
            Long senderWalletId,
            Long receiverWalletId,
            BigDecimal amount) {

        ObjectOptimisticLockingFailureException lastConflict = null;

        for (int attempt = 1; attempt <= MAX_TRANSFER_ATTEMPTS; attempt++) {
            try {
                return self.attemptTransfer(
                        senderWalletId, receiverWalletId, amount);
            } catch (ObjectOptimisticLockingFailureException conflict) {
                lastConflict = conflict;
                logger.warn("Transfer attempt {}: optimistic locking failed; retrying", attempt);
            }
        }

        throw lastConflict;
    }

    // The debit, credit, and transfer record are atomic:
    // either all three are committed, or none are.
    @Transactional(
            propagation = Propagation.REQUIRED,
            rollbackFor = Exception.class)
    public Transfer attemptTransfer(
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
            TransferFilter filter,
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

        return transfers.findByWalletId(walletId, filter, pageable);
    }

    @Transactional(readOnly = true)
    public Transfer findById(UUID transferId, String callerEmail) {

        // Existence is checked first, matching history(): unknown transfers
        // return 404; existing transfers owned by someone else return 403.
        Transfer transfer = transfers.findById(transferId)
                .orElseThrow(() ->
                        new TransferNotFoundException(transferId));

        Wallet callerWallet = walletService.getByEmail(callerEmail);

        boolean isSender = Objects.equals(
                transfer.getSenderWalletId(),
                callerWallet.getId());

        boolean isReceiver = Objects.equals(
                transfer.getReceiverWalletId(),
                callerWallet.getId());

        if (!isSender && !isReceiver) {
            throw new WalletAccessDeniedException(callerWallet.getId());
        }

        return transfer;
    }
}
