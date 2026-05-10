package com.azizsattarov.corebanking.admin.dto;

import com.azizsattarov.corebanking.transaction.ChainStatus;
import com.azizsattarov.corebanking.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full reconciliation view returned by /admin/transactions/* endpoints.
 * Contains every field the in-middleware worker needs to:
 *   - resubmit a hash (referenceId, amount, balanceAfter, type, accountNumber, createdAt → canonical hash)
 *   - poll Sepolia for confirmation (blockchainTx)
 *   - run tamper detection (canonicalHash + the same payload fields)
 */
public record AdminTransactionView(
        Long transactionId,
        String referenceId,
        String accountNumber,
        TransactionType transactionType,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDateTime createdAt,
        String canonicalHash,
        String blockchainTx,
        ChainStatus chainStatus,
        int submitAttempts,
        String lastSubmitError
) {}
