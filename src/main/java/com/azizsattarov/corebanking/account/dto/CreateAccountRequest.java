package com.azizsattarov.corebanking.account.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotNull @PositiveOrZero BigDecimal initialBalance
) {}
