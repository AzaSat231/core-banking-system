package com.azizsattarov.corebanking.atm.dto;

public record RegisterFingerprintRequest(
        String accountNumber,
        int fingerprintSlotId
) {}
