package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.account.AccountStatus;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import com.azizsattarov.corebanking.transaction.dto.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService{
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final EntityManager em;

    public TransactionServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository, EntityManager em) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.em = em;
    }

    private String generateReferenceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private Transaction setTransaction(BigDecimal amount, BigDecimal balanceAfter, TransactionType transactionType, TransactionStatus status, String referenceId){
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setTransactionType(transactionType);
        transaction.setTransactionStatus(status);
        transaction.setReferenceId(referenceId);

        return transaction;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(Long accountId, DepositRequest depositRequest){
//        em.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        if (!account.isActive()){
            Transaction transaction = setTransaction(
                    depositRequest.amountDeposit(),
                    account.getBalance(),
                    TransactionType.DEPOSIT,
                    TransactionStatus.DECLINED,
                    generateReferenceId()
            );

            account.addTransaction(transaction);
            accountRepository.save(account);
            throw new BadRequestException("Deposit failed: This account is " + account.getAccountStatus());
        }

        if (depositRequest.amountDeposit().compareTo(BigDecimal.ZERO) <= 0){
            Transaction transaction = setTransaction(
                    depositRequest.amountDeposit(),
                    account.getBalance(),
                    TransactionType.DEPOSIT,
                    TransactionStatus.DECLINED,
                    generateReferenceId()
            );

            account.addTransaction(transaction);
            accountRepository.save(account);
            throw new BadRequestException("Deposit Amount must be positive");
        }

        BigDecimal balanceAfter = account.getBalance().add(depositRequest.amountDeposit());

        Transaction transaction = setTransaction(
                depositRequest.amountDeposit(),
                balanceAfter,
                TransactionType.DEPOSIT,
                TransactionStatus.APPROVED,
                generateReferenceId()
        );

        account.setBalance(balanceAfter);
        account.addTransaction(transaction);

        accountRepository.save(account);

        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getReferenceId(),
                transaction.getAmount(),
                transaction.getCreatedAt(),
                transaction.getTransactionType()
        );
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(Long accountId, WithdrawRequest withdrawRequest) {
//        em.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        if (!account.isActive()){
            Transaction transaction = setTransaction(
                    withdrawRequest.amountWithdraw(),
                    account.getBalance(),
                    TransactionType.WITHDRAW,
                    TransactionStatus.DECLINED,
                    generateReferenceId()
            );

            account.addTransaction(transaction);
            accountRepository.save(account);

            throw new BadRequestException("Withdraw failed: This account is " + account.getAccountStatus());
        }

        if (withdrawRequest.amountWithdraw().compareTo(account.getBalance()) > 0){
            Transaction transaction = setTransaction(
                    withdrawRequest.amountWithdraw(),
                    account.getBalance(),
                    TransactionType.WITHDRAW,
                    TransactionStatus.DECLINED,
                    generateReferenceId()
            );

            account.addTransaction(transaction);
            accountRepository.save(account);

            throw new BadRequestException("Withdraw Amount must be less than Balance Amount");
        }

        if (withdrawRequest.amountWithdraw().compareTo(BigDecimal.ZERO) <= 0){
            Transaction transaction = setTransaction(
                    withdrawRequest.amountWithdraw(),
                    account.getBalance(),
                    TransactionType.WITHDRAW,
                    TransactionStatus.DECLINED,
                    generateReferenceId()
            );

            account.addTransaction(transaction);
            accountRepository.save(account);

            throw new BadRequestException("Withdraw Amount must be positive");
        }

        BigDecimal balanceAfter = account.getBalance().subtract(withdrawRequest.amountWithdraw());

        Transaction transaction = setTransaction(
                withdrawRequest.amountWithdraw(),
                balanceAfter,
                TransactionType.WITHDRAW,
                TransactionStatus.APPROVED,
                generateReferenceId()
        );

        account.setBalance(balanceAfter);
        account.addTransaction(transaction);

        accountRepository.save(account);

        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getReferenceId(),
                transaction.getAmount(),
                transaction.getCreatedAt(),
                transaction.getTransactionType()
        );
    }

    @Override
    @Transactional
    public TransferResponse transfer(Long accountFromId, TransferRequest transferRequest) {
        if (transferRequest.amount().compareTo(BigDecimal.ZERO) <= 0){
            throw new BadRequestException("Transfer Amount must be positive");
        }

        if (accountFromId.equals(transferRequest.toAccountId())){
            throw new BadRequestException("Transfer Accounts must be different");
        }

        // Lock rows in a consistent order to avoid deadlocks
        Long first = accountFromId < transferRequest.toAccountId() ? accountFromId : transferRequest.toAccountId();
        Long second = accountFromId < transferRequest.toAccountId() ? transferRequest.toAccountId() : accountFromId;

        Account a1 = accountRepository.findByIdForUpdate(first)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + first));

        Account a2 = accountRepository.findByIdForUpdate(second)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + second));

        Account accountFrom = accountFromId.equals(a1.getAccountId()) ? a1 : a2;
        Account accountTo = transferRequest.toAccountId().equals(a1.getAccountId()) ? a1 : a2;

        if (!accountFrom.isActive()){
            Transaction declined = setTransaction(
                    transferRequest.amount(),
                    accountFrom.getBalance(),
                    TransactionType.TRANSFER_OUT,
                    TransactionStatus.DECLINED,
                    generateReferenceId()
            );
            accountFrom.addTransaction(declined);
            accountRepository.save(accountFrom);

            throw new BadRequestException("Transfer failed: Account " + accountFrom.getAccountNumber() + " is " + accountFrom.getAccountStatus());
        }

        if (!accountTo.isActive()){
            Transaction declined = setTransaction(
                    transferRequest.amount(),
                    accountTo.getBalance(),
                    TransactionType.TRANSFER_IN,
                    TransactionStatus.DECLINED,
                    generateReferenceId()
            );

            accountTo.addTransaction(declined);
            accountRepository.save(accountTo);

            throw new BadRequestException("Transfer failed: Account " + accountTo.getAccountNumber() + " is " + accountTo.getAccountStatus());
        }

        if (transferRequest.amount().compareTo(accountFrom.getBalance()) > 0){
            Transaction declined = setTransaction(
                    transferRequest.amount(),
                    accountFrom.getBalance(),
                    TransactionType.TRANSFER_OUT,
                    TransactionStatus.DECLINED,
                    generateReferenceId()
            );
            accountFrom.addTransaction(declined);
            accountRepository.save(accountFrom);

            throw new BadRequestException("Insufficient funds");
        }

        String ref = generateReferenceId();

        BigDecimal balanceAfterFrom = accountFrom.getBalance().subtract(transferRequest.amount());
        BigDecimal balanceAfterTo = accountTo.getBalance().add(transferRequest.amount());

        Transaction transactionFrom = setTransaction(
                transferRequest.amount(),
                balanceAfterFrom,
                TransactionType.TRANSFER_OUT,
                TransactionStatus.APPROVED,
                ref
        );

        transactionFrom.setCounterpartyAccountNumber(accountTo.getAccountNumber());

        Transaction transactionTo = setTransaction(
                transferRequest.amount(),
                balanceAfterTo,
                TransactionType.TRANSFER_IN,
                TransactionStatus.APPROVED,
                ref
        );

        transactionTo.setCounterpartyAccountNumber(accountFrom.getAccountNumber());

        accountFrom.setBalance(balanceAfterFrom);
        accountTo.setBalance(balanceAfterTo);

        accountFrom.addTransaction(transactionFrom);
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
                transferRequest.toAccountId(),
                transferRequest.amount(),
                accountFrom.getBalance(),
                accountTo.getBalance(),
                savedFrom.getCreatedAt(),
                savedTo.getCreatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

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
