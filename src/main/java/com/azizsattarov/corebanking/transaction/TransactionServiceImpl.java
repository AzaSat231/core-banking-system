package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import com.azizsattarov.corebanking.transaction.dto.TransactionResponse;
import com.azizsattarov.corebanking.transaction.dto.TransferResponse;
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

    @Override
    @Transactional
    public TransactionResponse deposit(Long accountId, BigDecimal amountDeposit){
//        em.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        if (amountDeposit.compareTo(BigDecimal.ZERO) <= 0){
            throw new BadRequestException("Deposit Amount must be positive");
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(amountDeposit);
        transaction.setTransactionType(TransactionType.DEPOSIT);
        transaction.setReferenceId(UUID.randomUUID().toString());

        account.setBalance(account.getBalance().add(amountDeposit));
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
    public TransactionResponse withdraw(Long accountId, BigDecimal amountWithdraw) {
//        em.createNativeQuery("SET LOCAL lock_timeout = '2s'").executeUpdate();

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        if (amountWithdraw.compareTo(account.getBalance()) > 0){
            throw new BadRequestException("Withdraw Amount must be less than Balance Amount");
        }

        if (amountWithdraw.compareTo(BigDecimal.ZERO) <= 0){
            throw new BadRequestException("Withdraw Amount must be positive");
        }

        Transaction transaction = new Transaction();
        transaction.setAmount(amountWithdraw);
        transaction.setTransactionType(TransactionType.WITHDRAW);
        transaction.setReferenceId(UUID.randomUUID().toString());

        account.setBalance(account.getBalance().subtract(amountWithdraw));
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
    public TransferResponse transfer(Long accountFromId, Long accountToId, BigDecimal amountTransfer) {
        if (amountTransfer.compareTo(BigDecimal.ZERO) <= 0){
            throw new BadRequestException("Transfer Amount must be positive");
        }

        if (accountFromId.equals(accountToId)){
            throw new BadRequestException("Transfer Accounts must be different");
        }

        // Lock rows in a consistent order to avoid deadlocks
        Long first = accountFromId < accountToId ? accountFromId : accountToId;
        Long second = accountFromId < accountToId ? accountToId : accountFromId;

        Account a1 = accountRepository.findByIdForUpdate(first)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + first));

        Account a2 = accountRepository.findByIdForUpdate(second)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + second));

        Account accountFrom = accountFromId.equals(a1.getAccountId()) ? a1 : a2;
        Account accountTo = accountToId.equals(a1.getAccountId()) ? a1 : a2;

        if (amountTransfer.compareTo(accountFrom.getBalance()) > 0){
            throw new BadRequestException("Insufficient funds");
        }

        String ref = UUID.randomUUID().toString();

        Transaction transactionFrom = new Transaction();
        transactionFrom.setAmount(amountTransfer);
        transactionFrom.setTransactionType(TransactionType.TRANSFER_OUT);
        transactionFrom.setReferenceId(ref);

        Transaction transactionTo = new Transaction();
        transactionTo.setAmount(amountTransfer);
        transactionTo.setTransactionType(TransactionType.TRANSFER_IN);
        transactionTo.setReferenceId(ref);

        accountFrom.setBalance(accountFrom.getBalance().subtract(amountTransfer));
        accountTo.setBalance(accountTo.getBalance().add(amountTransfer));

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
