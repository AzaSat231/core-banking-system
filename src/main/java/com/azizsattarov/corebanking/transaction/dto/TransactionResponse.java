package com.azizsattarov.corebanking.transaction.dto;

import com.azizsattarov.corebanking.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long transactionId,
        BigDecimal amount,
        LocalDateTime createdAt,
        TransactionType transactionType
) {}
