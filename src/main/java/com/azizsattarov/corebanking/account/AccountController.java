package com.azizsattarov.corebanking.account;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/customers/{customerId}/accounts")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @PathVariable Long customerId,
            @RequestBody CreateAccountRequest req
    ) {
        AccountResponse created = accountService.createAccount(customerId, req.accountNumber(), req.initialBalance());
        return ResponseEntity.status(201).body(created);
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> removeAccount(@PathVariable Long customerId, @PathVariable Long accountId) {
        accountService.removeAccount(customerId, accountId);
        return ResponseEntity.noContent().build();
    }

    public record CreateAccountRequest(String accountNumber, BigDecimal initialBalance) {}
}
