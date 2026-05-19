package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.account.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @Column(nullable = false)
    private String referenceId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(nullable = true)
    private String counterpartyAccountNumber;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus transactionStatus;

    // ── Blockchain reconciliation fields (managed by middleware Layer 2) ──────
    // canonicalHash and blockchainTx are written via PATCH /admin/transactions/{id}/blockchain
    // after Spring Boot has committed the transaction and returned its id.
    @Column(length = 64)
    private String canonicalHash;

    @Column(length = 80)
    private String blockchainTx;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChainStatus chainStatus;

    @Column(nullable = false)
    private int submitAttempts;

    @Column(length = 1000)
    private String lastSubmitError;

    /** ATM withdraw: waiting for cash-dispense ACK, confirmed, or auto-reversed. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DispenseStatus dispenseStatus = DispenseStatus.NOT_APPLICABLE;

    /** When {@link DispenseStatus#PENDING_DISPENSE} and past this time, scheduler reverses. */
    @Column
    private LocalDateTime dispenseDeadline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.chainStatus == null) {
            this.chainStatus = ChainStatus.PENDING_SUBMIT;
        }
    }
}
