package com.azizsattarov.corebanking.account.dto;

import com.azizsattarov.corebanking.account.AccountStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long accountId,
        String accountNumber,
        AccountStatus accountStatus,
        BigDecimal balance,
        LocalDateTime createdAt
) {}
