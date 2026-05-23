package com.azizsattarov.corebanking.card;

import com.azizsattarov.corebanking.account.Account;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A physical/virtual card linked to one Account.
 * One account may have multiple cards (e.g. primary + additional).
 * The 16-digit card number is generated using the same Luhn-valid
 * algorithm already present in AccountServiceImpl.
 */
@Entity
@Getter
@Setter
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cardId;

    /** 16-digit Luhn-valid card number. Unique across the system. */
    @Column(nullable = false, unique = true, length = 16)
    private String cardNumber;

    @Column(name = "pin_hash")
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus cardStatus;

    /** MM/YY expiry — 3 years from issuance by default. */
    @Column(nullable = false)
    private LocalDate expiryDate;

    /** Card holder name printed on card (defaults to customer full name). */
    @Column(nullable = false, length = 26)
    private String holderName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime blockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    protected Card() {}

    public Card(String cardNumber, String holderName, LocalDate expiryDate) {
        this.cardNumber  = cardNumber;
        this.holderName  = holderName;
        this.expiryDate  = expiryDate;
        this.cardStatus  = CardStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.cardStatus == null) this.cardStatus = CardStatus.ACTIVE;
    }

    public boolean isActive() {
        return this.cardStatus == CardStatus.ACTIVE;
    }

    public boolean hasPin() { return this.pinHash != null; }
}
