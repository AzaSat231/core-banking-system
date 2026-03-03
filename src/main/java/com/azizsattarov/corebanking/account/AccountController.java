package com.azizsattarov.corebanking.account;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.account.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody CreateAccountRequest request
    ) {
        AccountResponse created = accountService.createAccount(customerId, request);
        return ResponseEntity.status(201).body(created);
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> removeAccount(@PathVariable Long customerId, @PathVariable Long accountId) {
        accountService.removeAccount(customerId, accountId);
        return ResponseEntity.noContent().build();
    }

}
