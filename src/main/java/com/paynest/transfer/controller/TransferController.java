package com.paynest.transfer.controller;

import com.paynest.transfer.dto.TransferRequest;
import com.paynest.transfer.dto.TransferResponse;
import com.paynest.transfer.service.TransferService;
import com.paynest.wallet.service.WalletService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

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

        return TransferResponse.from(transfer);
    }

    // Errors are mapped by GlobalExceptionHandler; no try/catch belongs here.
}
