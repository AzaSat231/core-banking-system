package com.azizsattarov.corebanking.customer.dto;

import com.azizsattarov.corebanking.account.dto.CreateAccountRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateCustomerRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String nationalId,
        @NotBlank @Email String email,
        @NotBlank String phoneNumber,
        @NotBlank LocalDate dateOfBirth
) {}
