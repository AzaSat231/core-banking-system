package com.azizsattarov.corebanking.atm.dto;

public record AccountFingerprintResponse(
        String accountNumber,
        Integer fingerprintSlotId
) {}
