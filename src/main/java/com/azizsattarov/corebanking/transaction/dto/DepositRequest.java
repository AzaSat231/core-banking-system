package com.azizsattarov.corebanking.transaction.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @DecimalMax(value = "100000.00", message = "Amount cannot exceed 100,000")
        @NotNull
        @NotNull @Positive BigDecimal amountDeposit
) {
}
