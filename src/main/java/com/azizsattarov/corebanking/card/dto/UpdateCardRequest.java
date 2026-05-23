package com.azizsattarov.corebanking.card.dto;

import com.azizsattarov.corebanking.card.CardStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCardRequest(
        @NotNull CardStatus cardStatus
) {}
