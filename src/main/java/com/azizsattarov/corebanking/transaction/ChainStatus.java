package com.azizsattarov.corebanking.transaction;

/**
 * Per-transaction blockchain reconciliation status.
 * Set and updated by the middleware (Layer 2) via /admin/transactions/* endpoints.
 *
 *  PENDING_SUBMIT → row created, canonical hash not yet sent to chain
 *  SUBMITTED      → canonical hash sent to chain, awaiting on-chain confirmation
 *  CONFIRMED      → on-chain receipt success; matches canonicalHash
 *  FAILED_SUBMIT  → submission keeps failing, retries exhausted (manual review)
 *  TAMPERED       → recomputed hash from DB row no longer matches chain hash
 */
public enum ChainStatus {
    PENDING_SUBMIT,
    SUBMITTED,
    CONFIRMED,
    FAILED_SUBMIT,
    TAMPERED
}
