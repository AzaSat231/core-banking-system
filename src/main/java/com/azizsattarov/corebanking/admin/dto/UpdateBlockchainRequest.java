package com.azizsattarov.corebanking.admin.dto;

/**
 * PATCH body for /admin/transactions/{id}/blockchain.
 * blockchainTx may be null if submission is being deferred to a worker retry.
 */
public record UpdateBlockchainRequest(
        String canonicalHash,
        String blockchainTx,
        String submitError
) {}
