package com.azizsattarov.corebanking.admin.event;

/**
 * Published when a transaction's chain status transitions to FAILED_SUBMIT
 * after exhausting submit retries.
 */
public record BlockchainSubmitFailedEvent(
        Long transactionId,
        String accountNumber,
        String lastSubmitError,
        int submitAttempts
) {}
