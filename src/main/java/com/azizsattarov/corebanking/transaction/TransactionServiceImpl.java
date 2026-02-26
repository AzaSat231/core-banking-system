package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.transaction.dto.TransactionResponse;
import com.azizsattarov.corebanking.transaction.dto.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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

        String ref = UUID.randomUUID().toString();

        transaction.setReferenceId(ref);

        account.setBalance(account.getBalance().add(amountDeposit));

        account.addTransaction(transaction);

        Transaction saved = transactionRepository.save(transaction);

        return new TransactionResponse(
                saved.getTransactionId(),
                saved.getReferenceId(),
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

        String ref = UUID.randomUUID().toString();

        transaction.setReferenceId(ref);

        account.setBalance(account.getBalance().subtract(amountWithdraw));

        account.addTransaction(transaction);

        Transaction saved = transactionRepository.save(transaction);

        return new TransactionResponse(
                saved.getTransactionId(),
                saved.getReferenceId(),
                saved.getAmount(),
                saved.getCreatedAt(),
                saved.getTransactionType()
        );
    }

    @Override
    @Transactional
    public TransferResponse transfer(Long accountFromId, Long accountToId, BigDecimal amountTransfer) {
        if (amountTransfer.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Transfer Amount must be positive");
        }

        if (accountFromId.equals(accountToId)){
            throw new IllegalArgumentException("Transfer Accounts must be different");
        }

        // Lock rows in a consistent order to avoid deadlocks
        Long first = accountFromId < accountToId ? accountFromId : accountToId;
        Long second = accountFromId < accountToId ? accountToId : accountFromId;

        Account a1 = accountRepository.findByIdForUpdate(first)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        Account a2 = accountRepository.findByIdForUpdate(second)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        Account accountFrom = accountFromId.equals(a1.getAccountId()) ? a1 : a2;
        Account accountTo = accountToId.equals(a1.getAccountId()) ? a1 : a2;

        if (amountTransfer.compareTo(accountFrom.getBalance()) > 0){
            throw new IllegalArgumentException("Insufficient funds");
        }

        String ref = UUID.randomUUID().toString();


        Transaction transactionFrom = new Transaction();
        transactionFrom.setAmount(amountTransfer);
        transactionFrom.setTransactionType(TransactionType.TRANSFER_OUT);

        transactionFrom.setReferenceId(ref);

        accountFrom.setBalance(accountFrom.getBalance().subtract(amountTransfer));

        accountFrom.addTransaction(transactionFrom);

        Transaction transactionTo = new Transaction();
        transactionTo.setAmount(amountTransfer);
        transactionTo.setTransactionType(TransactionType.TRANSFER_IN);

        transactionTo.setReferenceId(ref);

        accountTo.setBalance(accountTo.getBalance().add(amountTransfer));

        accountTo.addTransaction(transactionTo);

        accountRepository.save(accountFrom);
        accountRepository.save(accountTo);

        Transaction savedFrom = transactionRepository.save(transactionFrom);
        Transaction savedTo   = transactionRepository.save(transactionTo);

        return new TransferResponse(
                ref,
                savedFrom.getTransactionId(),
                savedTo.getTransactionId(),
                accountFromId,
                accountToId,
                amountTransfer,
                accountFrom.getBalance(),
                accountTo.getBalance(),
                savedFrom.getCreatedAt(),
                savedTo.getCreatedAt()
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
                        t.getReferenceId(),
                        t.getAmount(),
                        t.getCreatedAt(),
                        t.getTransactionType()
                ))
                .toList();
    }

}
