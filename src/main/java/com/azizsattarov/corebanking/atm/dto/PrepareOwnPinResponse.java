package com.azizsattarov.corebanking.atm.dto;

public record PrepareOwnPinResponse(
        Long cardId,
        String maskedNumber,
        String accountNumber,
        boolean hadExistingCards,
        Integer fingerprintSlotId
) {}
