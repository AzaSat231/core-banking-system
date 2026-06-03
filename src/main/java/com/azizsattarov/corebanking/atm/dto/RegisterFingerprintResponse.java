package com.azizsattarov.corebanking.atm.dto;

public record RegisterFingerprintResponse(
        String status,
        String message,
        int fingerprintSlotId
) {}
