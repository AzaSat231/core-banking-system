package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import com.azizsattarov.corebanking.transaction.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final int defaultAckTimeoutSeconds;
    private final int reversalBatchSize;

    public TransactionServiceImpl(AccountRepository accountRepository,
                                  TransactionRepository transactionRepository,
                                  @Value("${dispense.ack-timeout-seconds:30}") int defaultAckTimeoutSeconds,
                                  @Value("${dispense.reversal-batch-size:50}") int reversalBatchSize) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.defaultAckTimeoutSeconds = defaultAckTimeoutSeconds;
        this.reversalBatchSize = reversalBatchSize;
    }


    private void assertOwnership(Account account) {

        // Step 2: get the JWT principal from Spring Security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) return;
        String principal = auth.getName();

        // Step 3: only check if caller is an ATM session (starts with ATM_)
        // Admin JWTs (subject = "admin") skip this check intentionally
        // ATM sessions have subject "ATM_<accountNumber>"
        if (principal.startsWith("ATM_")) {
            String expected = "ATM_" + account.getAccountNumber();

            // Step 4: build what the principal SHOULD be for this account
            // and compare against what it actually IS
            if (!principal.equals(expected)) {
                throw new BadRequestException("Forbidden: account mismatch");
            }
        }
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionId(),
                transaction.getReferenceId(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt(),
                transaction.getTransactionType(),
                transaction.getChainStatus(),
                transaction.getDispenseStatus(),
                transaction.getDispenseDeadline());
    }

    private String generateReferenceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /**
     * Idempotency replay check. MUST be called while holding the account write lock
     * ({@link AccountRepository#findByIdForUpdate}) so that concurrent requests for
     * the same account are serialized and an already-committed transaction with this
     * key is visible. Returns the original response on a replay, or {@code null} when
     * this is a fresh request that should proceed.
     */
    private TransactionResponse replayIfDuplicate(String requestKey, Long accountId) {
        if (requestKey == null || requestKey.isBlank()) {
            return null;
        }
        return transactionRepository.findByRequestKey(requestKey)
                .map(existing -> {
                    if (!existing.getAccount().getAccountId().equals(accountId)) {
                        throw new BadRequestException(
                                "Idempotency key already used for a different account");
                    }
                    return toResponse(existing);
                })
                .orElse(null);
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
        t.setDispenseStatus(DispenseStatus.NOT_APPLICABLE);
        return t;
    }

    @Override
    @Transactional
    public TransactionResponse deposit(Long accountId, DepositRequest depositRequest, String requestKey) {
        // Fast ownership pre-check without lock
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName().startsWith("ATM_")) {
            Account preCheck = accountRepository.findById(accountId)
                    .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));
            if (!auth.getName().equals("ATM_" + preCheck.getAccountNumber())) {
                throw new BadRequestException("Forbidden: account mismatch");
            }
        }

        // Now acquire the lock
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        // Idempotency: under the account lock, replay the original if this key already ran.
        TransactionResponse replay = replayIfDuplicate(requestKey, accountId);
        if (replay != null) {
            return replay;
        }

        assertOwnership(account);

        if (!account.isActive())
            throw new BadRequestException("Deposit failed: This account is " + account.getAccountStatus());
        if (depositRequest.amountDeposit().compareTo(BigDecimal.ZERO) <= 0)
            throw new BadRequestException("Deposit Amount must be positive");

        BigDecimal balanceAfter = account.getBalance().add(depositRequest.amountDeposit());

        Transaction transaction = setTransaction(
                depositRequest.amountDeposit(), balanceAfter,
                TransactionType.DEPOSIT, TransactionStatus.APPROVED, generateReferenceId());
        transaction.setRequestKey(requestKey);

        // FIX: set account on transaction, update balance, then save once via transactionRepository.
        // accountRepository.save() + transactionRepository.save() was inserting two rows
        // because CascadeType.ALL on Account.transactions already persists on accountRepository.save().
        account.setBalance(balanceAfter);
        transaction.setAccount(account);
        transaction = transactionRepository.save(transaction);

        return toResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse withdraw(Long accountId,
                                        WithdrawRequest withdrawRequest,
                                        Integer ackTimeoutSeconds,
                                        String requestKey) {
        // Fast ownership pre-check without lock
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName().startsWith("ATM_")) {
            Account preCheck = accountRepository.findById(accountId)
                    .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));
            if (!auth.getName().equals("ATM_" + preCheck.getAccountNumber())) {
                throw new BadRequestException("Forbidden: account mismatch");
            }
        }

        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        // Idempotency: under the account lock, replay the original if this key already ran.
        TransactionResponse replay = replayIfDuplicate(requestKey, accountId);
        if (replay != null) {
            return replay;
        }

        assertOwnership(account);

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
        transaction.setRequestKey(requestKey);

        int timeout = ackTimeoutSeconds != null && ackTimeoutSeconds > 0
                ? ackTimeoutSeconds
                : defaultAckTimeoutSeconds;
        transaction.setDispenseStatus(DispenseStatus.PENDING_DISPENSE);
        transaction.setDispenseDeadline(LocalDateTime.now().plusSeconds(timeout));

        account.setBalance(balanceAfter);
        transaction.setAccount(account);
        transaction = transactionRepository.save(transaction);

        return toResponse(transaction);
    }

    @Override
    @Transactional
    public TransactionResponse confirmDispense(Long accountId, Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction Not Found: " + transactionId));

        if (!transaction.getAccount().getAccountId().equals(accountId)) {
            throw new BadRequestException("Transaction does not belong to this account");
        }

        assertOwnership(transaction.getAccount());

        if (transaction.getTransactionType() != TransactionType.WITHDRAW) {
            throw new BadRequestException("Only withdrawals require dispense confirmation");
        }
        if (transaction.getDispenseStatus() == DispenseStatus.DISPENSED) {
            return toResponse(transaction);
        }
        if (transaction.getDispenseStatus() != DispenseStatus.PENDING_DISPENSE) {
            throw new BadRequestException(
                    "Withdraw is not awaiting dispense confirmation (status: "
                            + transaction.getDispenseStatus() + ")");
        }

        transaction.setDispenseStatus(DispenseStatus.DISPENSED);
        transaction.setDispenseDeadline(null);
        transaction = transactionRepository.save(transaction);
        return toResponse(transaction);
    }

    @Override
    @Transactional
    public int reverseExpiredPendingDispenses() {
        List<Transaction> expired = transactionRepository.findExpiredPendingDispense(
                LocalDateTime.now(), PageRequest.of(0, reversalBatchSize));
        int reversed = 0;
        for (Transaction withdrawTx : expired) {
            reversePendingWithdraw(withdrawTx);
            reversed++;
        }
        return reversed;
    }

    private void reversePendingWithdraw(Transaction withdrawTx) {
        if (withdrawTx.getDispenseStatus() != DispenseStatus.PENDING_DISPENSE) {
            return;
        }

        Account account = accountRepository.findByIdForUpdate(
                        withdrawTx.getAccount().getAccountId())
                .orElseThrow(() -> new NotFoundException(
                        "Account Not Found: " + withdrawTx.getAccount().getAccountId()));

        BigDecimal balanceAfter = account.getBalance().add(withdrawTx.getAmount());

        Transaction reversal = setTransaction(
                withdrawTx.getAmount(),
                balanceAfter,
                TransactionType.DEPOSIT,
                TransactionStatus.APPROVED,
                generateReferenceId());
        reversal.setAccount(account);
        account.setBalance(balanceAfter);
        transactionRepository.save(reversal);

        withdrawTx.setDispenseStatus(DispenseStatus.REVERSED);
        withdrawTx.setDispenseDeadline(null);
        transactionRepository.save(withdrawTx);
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

        assertOwnership(accountFrom);

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
        // Step 1: load the account from DB using the URL accountId
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        // Check if accessible to the account number or not
        assertOwnership(account);

        return transactionRepository.findByAccountId(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
}