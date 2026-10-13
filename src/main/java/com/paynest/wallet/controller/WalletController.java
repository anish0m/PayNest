package com.paynest.wallet.controller;

import com.paynest.wallet.dto.WalletResponse;
import com.paynest.wallet.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public WalletResponse me(Principal principal) {
        return WalletResponse.from(service.getByEmail(principal.getName()));
    }
}
