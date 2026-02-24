package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService{
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(Long accountId, BigDecimal amountDeposit){
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        if (amountDeposit.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Deposit Amount must be positive");
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(amountDeposit);
        transaction.setTransactionType(TransactionType.DEPOSIT);

        account.setBalance(account.getBalance().add(amountDeposit));

        account.addTransaction(transaction);

        Transaction saved = transactionRepository.save(transaction);

        return new TransactionResponse(
                saved.getTransactionId(),
                saved.getAmount(),
                saved.getCreatedAt(),
                saved.getTransactionType()
        );
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(Long accountId, BigDecimal amountWithdraw) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        if (amountWithdraw.compareTo(account.getBalance()) > 0){
            throw new IllegalArgumentException("Withdraw Amount must be less than Balance Amount");
        }

        if (amountWithdraw.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Withdraw Amount must be positive");
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(amountWithdraw);
        transaction.setTransactionType(TransactionType.WITHDRAW);

        account.setBalance(account.getBalance().subtract(amountWithdraw));

        account.addTransaction(transaction);

        Transaction saved = transactionRepository.save(transaction);

        return new TransactionResponse(
                saved.getTransactionId(),
                saved.getAmount(),
                saved.getCreatedAt(),
                saved.getTransactionType()
        );
    }

    @Override
    @Transactional
    public List<TransactionResponse> getTransactionHistory(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        return account.getTransactions().stream()
                .map(t -> new TransactionResponse(
                        t.getTransactionId(),
                        t.getAmount(),
                        t.getCreatedAt(),
                        t.getTransactionType()
                ))
                .toList();
    }

}
