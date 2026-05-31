package com.azizsattarov.corebanking.admin.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Structured log for operators; middleware DLQ worker polls /failed-submit separately.
 */
@Component
public class BlockchainSubmitFailedListener {

    private static final Logger log = LoggerFactory.getLogger(BlockchainSubmitFailedListener.class);

    @EventListener
    public void onFailed(BlockchainSubmitFailedEvent event) {
        log.warn(
                "Blockchain submit failed for transactionId={} account={} attempts={} error={}",
                event.transactionId(),
                event.accountNumber(),
                event.submitAttempts(),
                event.lastSubmitError()
        );
    }
}
