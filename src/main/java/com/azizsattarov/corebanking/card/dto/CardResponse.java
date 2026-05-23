package com.azizsattarov.corebanking.card.dto;

import com.azizsattarov.corebanking.card.CardStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CardResponse(
        Long cardId,
        String cardNumber,
        String maskedNumber,   // e.g. **** **** **** 4242
        CardStatus cardStatus,
        String holderName,
        LocalDate expiryDate,
        Long accountId,
        String accountNumber,
        LocalDateTime createdAt
) {}
