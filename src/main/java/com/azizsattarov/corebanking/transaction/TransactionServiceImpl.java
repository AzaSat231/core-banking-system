package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import com.azizsattarov.corebanking.transaction.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(AccountRepository accountRepository,
                                  TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    private String generateReferenceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private Transaction setTransaction(BigDecimal amount, BigDecimal balanceAfter,
                                       TransactionType type, TransactionStatus status,
                                       String referenceId) {
        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setBalanceAfter(balanceAfter);
        t.setTransactionType(type);
        t.setTransactionStatus(status);
        t.setReferenceId(referenceId);
        return t;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(Long accountId, DepositRequest depositRequest) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        if (!account.isActive())
            throw new BadRequestException("Deposit failed: This account is " + account.getAccountStatus());
        if (depositRequest.amountDeposit().compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Deposit Amount must be positive");

        BigDecimal balanceAfter = account.getBalance().add(depositRequest.amountDeposit());

        Transaction transaction = setTransaction(
                depositRequest.amountDeposit(), balanceAfter,
                TransactionType.DEPOSIT, TransactionStatus.APPROVED, generateReferenceId());

        // FIX: set account on transaction, update balance, then save once via transactionRepository.
        // accountRepository.save() + transactionRepository.save() was inserting two rows
        // because CascadeType.ALL on Account.transactions already persists on accountRepository.save().
        account.setBalance(balanceAfter);
        transaction.setAccount(account);
        transaction = transactionRepository.save(transaction);

        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getReferenceId(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt(),
                transaction.getTransactionType(),
                transaction.getChainStatus());
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(Long accountId, WithdrawRequest withdrawRequest) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        if (!account.isActive())
            throw new BadRequestException("Withdraw failed: This account is " + account.getAccountStatus());
        if (withdrawRequest.amountWithdraw().compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Withdraw Amount must be positive");
        if (withdrawRequest.amountWithdraw().compareTo(account.getBalance()) > 0)
            throw new BadRequestException("Withdraw Amount must be less than Balance Amount");

        BigDecimal balanceAfter = account.getBalance().subtract(withdrawRequest.amountWithdraw());

        Transaction transaction = setTransaction(
                withdrawRequest.amountWithdraw(), balanceAfter,
                TransactionType.WITHDRAW, TransactionStatus.APPROVED, generateReferenceId());

        // FIX: same as deposit — one save only
        account.setBalance(balanceAfter);
        transaction.setAccount(account);
        transaction = transactionRepository.save(transaction);

        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getReferenceId(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt(),
                transaction.getTransactionType(),
                transaction.getChainStatus());
    }

    @Override
    @Transactional
    public TransferResponse transfer(Long accountFromId, TransferRequest transferRequest) {
        if (transferRequest.amount().compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Transfer Amount must be positive");
        if (accountFromId.equals(transferRequest.toAccountId()))
            throw new BadRequestException("Transfer Accounts must be different");

        Long first  = accountFromId < transferRequest.toAccountId() ? accountFromId : transferRequest.toAccountId();
        Long second = accountFromId < transferRequest.toAccountId() ? transferRequest.toAccountId() : accountFromId;

        Account a1 = accountRepository.findByIdForUpdate(first)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + first));
        Account a2 = accountRepository.findByIdForUpdate(second)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + second));

        Account accountFrom = accountFromId.equals(a1.getAccountId()) ? a1 : a2;
        Account accountTo   = transferRequest.toAccountId().equals(a1.getAccountId()) ? a1 : a2;

        if (!accountFrom.isActive())
            throw new BadRequestException("Transfer failed: Account " + accountFrom.getAccountNumber() + " is " + accountFrom.getAccountStatus());
        if (!accountTo.isActive())
            throw new BadRequestException("Transfer failed: Account " + accountTo.getAccountNumber() + " is " + accountTo.getAccountStatus());
        if (transferRequest.amount().compareTo(accountFrom.getBalance()) > 0)
            throw new BadRequestException("Insufficient funds");

        String ref = generateReferenceId();
        BigDecimal balanceAfterFrom = accountFrom.getBalance().subtract(transferRequest.amount());
        BigDecimal balanceAfterTo   = accountTo.getBalance().add(transferRequest.amount());

        Transaction txFrom = setTransaction(transferRequest.amount(), balanceAfterFrom,
                TransactionType.TRANSFER_OUT, TransactionStatus.APPROVED, ref);
        txFrom.setCounterpartyAccountNumber(accountTo.getAccountNumber());
        txFrom.setAccount(accountFrom);

        Transaction txTo = setTransaction(transferRequest.amount(), balanceAfterTo,
                TransactionType.TRANSFER_IN, TransactionStatus.APPROVED, ref);
        txTo.setCounterpartyAccountNumber(accountFrom.getAccountNumber());
        txTo.setAccount(accountTo);

        // FIX: update balances, then save both transactions once each
        accountFrom.setBalance(balanceAfterFrom);
        accountTo.setBalance(balanceAfterTo);
        txFrom = transactionRepository.save(txFrom);
        txTo   = transactionRepository.save(txTo);

        return new TransferResponse(
                ref,
                txFrom.getTransactionId(),
                txTo.getTransactionId(),
                accountFromId,
                transferRequest.toAccountId(),
                transferRequest.amount(),
                accountFrom.getBalance(),
                accountTo.getBalance(),
                txFrom.getCreatedAt(),
                txTo.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(Long accountId) {
        if (!accountRepository.existsById(accountId))
            throw new NotFoundException("Account Not Found: " + accountId);

        return transactionRepository.findByAccountId(accountId)
                .stream()
                .map(t -> new TransactionResponse(
                        t.getTransactionId(),
                        t.getReferenceId(),
                        t.getAmount(),
                        t.getBalanceAfter(),
                        t.getCreatedAt(),
                        t.getTransactionType(),
                        t.getChainStatus()))
                .toList();
    }
}