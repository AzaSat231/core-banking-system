package com.azizsattarov.corebanking.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long accountId,
        String accountNumber,
        BigDecimal balance,
        LocalDateTime createdAt
) {}
