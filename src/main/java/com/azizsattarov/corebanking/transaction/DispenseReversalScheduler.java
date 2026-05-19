package com.azizsattarov.corebanking.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reverses ATM withdrawals that were debited but never received a dispense ACK
 * before {@code dispenseDeadline}.
 */
@Component
public class DispenseReversalScheduler {

    private static final Logger log = LoggerFactory.getLogger(DispenseReversalScheduler.class);

    private final TransactionService transactionService;

    public DispenseReversalScheduler(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Scheduled(fixedDelayString = "${dispense.reversal-poll-ms:5000}")
    public void pollExpiredDispenses() {
        int reversed = transactionService.reverseExpiredPendingDispenses();
        if (reversed > 0) {
            log.info("Auto-reversed {} pending ATM withdrawal(s) (no dispense ACK in time)", reversed);
        }
    }
}
