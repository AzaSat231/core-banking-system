package com.azizsattarov.corebanking.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        String transactionReference,
        Long transactionFromId,
        Long transactionToId,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        BigDecimal fromNewBalance,
        BigDecimal toNewBalance,
        LocalDateTime createdFromAt,
        LocalDateTime createdToAt
) {}
