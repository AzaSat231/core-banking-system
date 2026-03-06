package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.transaction.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounts")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService){this.transactionService = transactionService; }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<TransactionResponse> deposit (
            @PathVariable Long accountId,
            @Valid @RequestBody DepositRequest depositRequest
            ){
        TransactionResponse deposited = transactionService.deposit(accountId, depositRequest);
        return ResponseEntity.status(201).body(deposited);
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw (
            @PathVariable Long accountId,
            @Valid @RequestBody WithdrawRequest withdrawRequest
            ){
        TransactionResponse withdrawn = transactionService.withdraw(accountId, withdrawRequest);
        return ResponseEntity.status(201).body(withdrawn);
    }

    @PostMapping("/{fromAccountId}/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @PathVariable Long fromAccountId,
            @Valid @RequestBody TransferRequest transferRequest
    ){
        TransferResponse transferResponse = transactionService.transfer(fromAccountId, transferRequest);
        return ResponseEntity.status(201).body(transferResponse);
    }

    @GetMapping("/{accountId}/transactions")
    public List<TransactionResponse> getTransactionHistory(@PathVariable Long accountId){return transactionService.getTransactionHistory(accountId); }

}

