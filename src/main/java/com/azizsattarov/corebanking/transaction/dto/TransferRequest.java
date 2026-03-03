package com.azizsattarov.corebanking.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank Long toAccountId,
        @NotNull @PositiveOrZero BigDecimal amount
) {
}
