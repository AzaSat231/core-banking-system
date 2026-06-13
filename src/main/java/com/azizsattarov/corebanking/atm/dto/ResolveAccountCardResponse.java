package com.azizsattarov.corebanking.atm.dto;

public record ResolveAccountCardResponse(
        String accountNumber,
        String cardNumber
) {}
