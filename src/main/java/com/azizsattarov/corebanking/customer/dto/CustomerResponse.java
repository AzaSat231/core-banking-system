package com.azizsattarov.corebanking.customer.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerResponse(
        Long customerId,      // returned to client, never sent by client
        String firstName,
        String lastName,
        String nationalId,
        String email,
        String phoneNumber,
        LocalDate dateOfBirth,
        LocalDateTime createdAt
) {}