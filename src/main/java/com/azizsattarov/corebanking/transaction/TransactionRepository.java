package com.azizsattarov.corebanking.transaction;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("SELECT t FROM Transaction t WHERE t.account.accountId = :accountId ORDER BY t.createdAt DESC")
    List<Transaction> findByAccountId(@Param("accountId") Long accountId);

    /**
     * Transactions whose canonical hash has not yet been written to chain.
     * Skip rows that have failed too many times (those need manual review).
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.chainStatus IN (com.azizsattarov.corebanking.transaction.ChainStatus.PENDING_SUBMIT,
                                    com.azizsattarov.corebanking.transaction.ChainStatus.FAILED_SUBMIT)
              AND t.submitAttempts < :maxAttempts
            ORDER BY t.createdAt ASC
            """)
    List<Transaction> findPendingSubmit(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    /**
     * Transactions submitted to chain but not yet confirmed (mined).
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.chainStatus = com.azizsattarov.corebanking.transaction.ChainStatus.SUBMITTED
            ORDER BY t.createdAt ASC
            """)
    List<Transaction> findSubmitted(Pageable pageable);

    /**
     * Confirmed transactions to run periodic tamper checks on.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.chainStatus = com.azizsattarov.corebanking.transaction.ChainStatus.CONFIRMED
              AND t.createdAt >= :since
            ORDER BY t.createdAt ASC
            """)
    List<Transaction> findConfirmedSince(@Param("since") LocalDateTime since, Pageable pageable);
}
