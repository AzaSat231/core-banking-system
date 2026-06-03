package com.azizsattarov.corebanking.card;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CardRepository extends JpaRepository<Card, Long> {

    @Query("SELECT c FROM Card c WHERE c.account.accountId = :accountId ORDER BY c.createdAt DESC")
    List<Card> findByAccountId(@Param("accountId") Long accountId);

    Optional<Card> findByCardNumber(String cardNumber);

    boolean existsByCardNumber(String cardNumber);

    @Query("""
            SELECT COUNT(c) FROM Card c
            WHERE c.account.accountId = :accountId
              AND c.pinHash IS NOT NULL
              AND c.cardStatus NOT IN (
                  com.azizsattarov.corebanking.card.CardStatus.CANCELLED,
                  com.azizsattarov.corebanking.card.CardStatus.EXPIRED)
              AND c.expiryDate >= CURRENT_DATE
            """)
    long countActiveCardsWithPin(@Param("accountId") Long accountId);
}
