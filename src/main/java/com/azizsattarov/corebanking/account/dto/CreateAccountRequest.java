package com.azizsattarov.corebanking.account.dto;

import java.math.BigDecimal;

public record CreateAccountRequest(
        String accountNumber,
        BigDecimal initialBalance
) {}
