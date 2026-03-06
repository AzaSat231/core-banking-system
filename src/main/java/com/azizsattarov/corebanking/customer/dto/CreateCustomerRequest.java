package com.azizsattarov.corebanking.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String nationalId,
        @NotBlank @Email String email,
        @NotBlank String phoneNumber,
        @NotNull LocalDate dateOfBirth
) {}
