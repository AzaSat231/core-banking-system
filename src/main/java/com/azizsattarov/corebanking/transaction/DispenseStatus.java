package com.azizsattarov.corebanking.transaction;

/**
 * Cash-dispense lifecycle for ATM withdrawals.
 * Deposits and transfers use {@link #NOT_APPLICABLE}.
 */
public enum DispenseStatus {
    NOT_APPLICABLE,
    PENDING_DISPENSE,
    DISPENSED,
    REVERSED
}
