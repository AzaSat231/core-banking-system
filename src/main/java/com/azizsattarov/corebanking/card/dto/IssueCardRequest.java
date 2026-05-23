package com.azizsattarov.corebanking.card.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IssueCardRequest(
        /** Optional override; defaults to customer full name if blank. */
        @Size(max = 26)
        String holderName
) {}
