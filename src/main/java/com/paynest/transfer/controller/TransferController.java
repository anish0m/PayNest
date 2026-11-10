package com.paynest.transfer.controller;

import com.paynest.transfer.dto.TransferRequest;
import com.paynest.transfer.dto.TransferResponse;
import com.paynest.transfer.service.TransferService;
import com.paynest.transfer.dto.TransferFilter;

import com.paynest.wallet.service.WalletService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

import java.security.Principal;

import java.util.UUID;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transfers;
    private final WalletService wallets;

    public TransferController(
            TransferService transfers,
            WalletService wallets) {
        this.transfers = transfers;
        this.wallets = wallets;
    }

    @PostMapping
    public TransferResponse create(
            Principal principal,
            @Valid @RequestBody TransferRequest request) {
        var senderWallet = wallets.getByEmail(principal.getName());

        var transfer = transfers.transfer(
                senderWallet.getId(),
                request.receiverWalletId(),
                request.amount());

        return TransferResponse.from(transfer, senderWallet.getId());
    }

    @GetMapping("/wallets/{walletId}")
    public Page<TransferResponse> history(
            Principal principal,
            @PathVariable Long walletId,
            @Valid TransferFilter filter,
            @PageableDefault(size = 20) Pageable pageable) {

        // History is always newest-first; caller-provided sort is ignored.
        // history() already verifies that walletId belongs to the caller, so it is
        // the correct viewer without an additional wallet lookup.
        return transfers
                .history(walletId, principal.getName(), filter, pageable)
                .map(transfer -> TransferResponse.from(transfer, walletId));
    }

    /*
     * Observed on the wire, 2026-11-10:
     * - insufficient funds -> 409 Conflict
     * - self-transfer -> 422 Unprocessable Entity
     * - unknown receiver or history wallet -> 404 Not Found
     * - no Authorization header -> 401 Unauthorized
     * - amount 0.00 -> 400 Bad Request
     * - existing wallet not owned by the caller -> 403 Forbidden
     *
     * History checks whether the wallet exists before checking ownership:
     * an unknown wallet returns 404, so unknown beats not-yours (403).
     */

    @GetMapping("/{transferId}")
    public TransferResponse details(
            Principal principal,
            @PathVariable UUID transferId) {

        var viewerWallet = wallets.getByEmail(principal.getName());

        return TransferResponse.from(
                transfers.findById(transferId, principal.getName()),
                viewerWallet.getId());
    }
}
