package com.azizsattarov.corebanking.transaction.dto;

import com.azizsattarov.corebanking.transaction.ChainStatus;
import com.azizsattarov.corebanking.transaction.DispenseStatus;
import com.azizsattarov.corebanking.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long transactionId,
        String referenceId,
        BigDecimal amount,
        BigDecimal balanceAfter,
        LocalDateTime createdAt,
        TransactionType transactionType,
        ChainStatus chainStatus,
        DispenseStatus dispenseStatus,
        LocalDateTime dispenseDeadline
) {}
