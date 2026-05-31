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
            @Valid @RequestBody DepositRequest depositRequest,
            @RequestHeader(value = "X-Idempotency-Key", required = false)
            String idempotencyKey
            ){
        TransactionResponse deposited = transactionService.deposit(accountId, depositRequest, idempotencyKey);
        return ResponseEntity.status(201).body(deposited);
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw (
            @PathVariable Long accountId,
            @Valid @RequestBody WithdrawRequest withdrawRequest,
            @RequestHeader(value = "X-Dispense-Ack-Timeout-Seconds", required = false)
            Integer ackTimeoutSeconds,
            @RequestHeader(value = "X-Idempotency-Key", required = false)
            String idempotencyKey
            ){
        TransactionResponse withdrawn = transactionService.withdraw(
                accountId, withdrawRequest, ackTimeoutSeconds, idempotencyKey);
        return ResponseEntity.status(201).body(withdrawn);
    }

    @PostMapping("/{accountId}/transactions/{transactionId}/confirm-dispense")
    public ResponseEntity<TransactionResponse> confirmDispense(
            @PathVariable Long accountId,
            @PathVariable Long transactionId) {
        TransactionResponse confirmed = transactionService.confirmDispense(accountId, transactionId);
        return ResponseEntity.ok(confirmed);
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

