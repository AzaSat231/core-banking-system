package com.azizsattarov.corebanking.transaction.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long toAccountId,
        @NotNull @PositiveOrZero BigDecimal amount
) {
}
