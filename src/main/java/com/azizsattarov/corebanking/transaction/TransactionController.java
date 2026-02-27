package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.transaction.dto.TransactionResponse;
import com.azizsattarov.corebanking.transaction.dto.TransferResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/accounts")
public class TransactionController {
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService){this.transactionService = transactionService; }

    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<TransactionResponse> deposit (
            @PathVariable Long accountId,
            @RequestBody TransactionController.CreateTransactionRequest req
    ){
        TransactionResponse deposited = transactionService.deposit(accountId, req.amount);
        return ResponseEntity.status(201).body(deposited);
    }

    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw (
            @PathVariable Long accountId,
            @RequestBody TransactionController.CreateTransactionRequest req
    ){
        TransactionResponse withdrawn = transactionService.withdraw(accountId, req.amount);
        return ResponseEntity.status(201).body(withdrawn);
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @RequestBody CreateTransferRequest req
    ){
        TransferResponse transferResponse = transactionService.transfer(req.fromAccountId, req.toAccountId, req.amount);
        return ResponseEntity.status(201).body(transferResponse);
    }

    @GetMapping("/{accountId}/transactions")
    public List<TransactionResponse> getTransactionHistory(@PathVariable Long accountId){return transactionService.getTransactionHistory(accountId); }

    public record CreateTransactionRequest(BigDecimal amount) {}
    public record CreateTransferRequest(Long fromAccountId, Long toAccountId, BigDecimal amount) {}
}

