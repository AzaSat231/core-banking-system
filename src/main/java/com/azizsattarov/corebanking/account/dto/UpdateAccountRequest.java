package com.azizsattarov.corebanking.account.dto;

import com.azizsattarov.corebanking.account.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountRequest(
        @NotNull AccountStatus accountStatus
) {}
