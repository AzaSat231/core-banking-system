package com.azizsattarov.corebanking.card;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.card.dto.CardResponse;
import com.azizsattarov.corebanking.card.dto.IssueCardRequest;
import com.azizsattarov.corebanking.card.dto.UpdateCardRequest;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

@Service
public class CardServiceImpl implements CardService {

    // BIN/IIN for cards — same bank prefix as account numbers.
    // Cards use a separate 8-digit BIN to distinguish them from account numbers.
    private static final String CARD_BIN = "62260099";
    private static final Random RANDOM     = new Random();
    private static final int    MAX_RETRIES = 10;

    private final CardRepository    cardRepository;
    private final AccountRepository accountRepository;

    public CardServiceImpl(CardRepository cardRepository, AccountRepository accountRepository) {
        this.cardRepository    = cardRepository;
        this.accountRepository = accountRepository;
    }

    // ── Luhn check digit (same algorithm used in AccountServiceImpl) ──────────
    private static int luhnCheckDigit(String number) {
        int sum = 0;
        boolean alt   = true;
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = Character.getNumericValue(number.charAt(i));
            if (alt) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
            alt = !alt;
        }
        return (10 - (sum % 10)) % 10;
    }

    private String generateCardNumber() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String mid       = String.format("%07d", RANDOM.nextInt(10_000_000)); // 7 digits like account
            String base15    = CARD_BIN + mid;                                    // 8 + 7 = 15
            int    check     = luhnCheckDigit(base15);                            // 1 Luhn digit
            String candidate = base15 + check;                                    // 16 total
            if (!cardRepository.existsByCardNumber(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not generate unique card number after " + MAX_RETRIES + " attempts");
    }

    private static String mask(String number) {
        if (number == null || number.length() != 16) return number;
        return "**** **** **** " + number.substring(12);
    }

    private CardResponse toResponse(Card card) {
        return new CardResponse(
                card.getCardId(),
                card.getCardNumber(),
                mask(card.getCardNumber()),
                card.getCardStatus(),
                card.getHolderName(),
                card.getExpiryDate(),
                card.getAccount().getAccountId(),
                card.getAccount().getAccountNumber(),
                card.getCreatedAt()
        );
    }

    // ── Service methods ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public CardResponse issueCard(Long accountId, IssueCardRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found: " + accountId));

        if (!account.isActive()) {
            throw new BadRequestException(
                    "Cannot issue card for a " + account.getAccountStatus() + " account");
        }

        String holderName = (request.holderName() != null && !request.holderName().isBlank())
                ? request.holderName().trim().toUpperCase()
                : (account.getCustomer().getFirstName() + " " + account.getCustomer().getLastName()).toUpperCase();

        if (holderName.length() > 26) {
            holderName = holderName.substring(0, 26);
        }

        Card card = new Card(
                generateCardNumber(),
                holderName,
                LocalDate.now().plusYears(3)   // 3-year expiry
        );
        card.setAccount(account);
        Card saved = cardRepository.save(card);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardResponse> getCardsByAccount(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new NotFoundException("Account not found: " + accountId);
        }
        return cardRepository.findByAccountId(accountId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public CardResponse updateCardStatus(Long cardId, UpdateCardRequest request) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Card not found: " + cardId));

        if (request.cardStatus() == CardStatus.BLOCKED && card.getBlockedAt() == null) {
            card.setBlockedAt(java.time.LocalDateTime.now());
        }
        card.setCardStatus(request.cardStatus());
        return toResponse(cardRepository.save(card));
    }

    @Override
    @Transactional
    public void cancelCard(Long cardId) {
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Card not found: " + cardId));
        card.setCardStatus(CardStatus.CANCELLED);
        cardRepository.save(card);
    }
}