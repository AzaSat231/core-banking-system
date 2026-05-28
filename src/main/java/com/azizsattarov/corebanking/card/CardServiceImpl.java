package com.azizsattarov.corebanking.card;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.card.dto.CardResponse;
import com.azizsattarov.corebanking.card.dto.IssueCardRequest;
import com.azizsattarov.corebanking.card.dto.UpdateCardRequest;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

@Service
public class CardServiceImpl implements CardService {

    // ── Industry limits ────────────────────────────────────────────────────────
    // Standard retail banking: 2–3 cards per account (primary + supplementary).
    // EU Payment Services Directive and most German retail banks cap at 3.
    // We set 3 as the ceiling; admin can cancel/replace but not exceed.
    private static final int MAX_CARDS_PER_ACCOUNT = 3;

    // BIN/IIN for cards — same bank prefix as account numbers.
    private static final String CARD_BIN = "62260099";
    private static final Random RANDOM     = new Random();
    private static final int    MAX_RETRIES = 10;

    private final CardRepository    cardRepository;
    private final AccountRepository accountRepository;
    private final CardEmailNotificationService cardEmailNotificationService;

    public CardServiceImpl(CardRepository cardRepository,
                           AccountRepository accountRepository,
                           CardEmailNotificationService cardEmailNotificationService) {
        this.cardRepository    = cardRepository;
        this.accountRepository = accountRepository;
        this.cardEmailNotificationService = cardEmailNotificationService;
    }

    // ── Luhn check digit ──────────────────────────────────────────────────────
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
            String mid       = String.format("%07d", RANDOM.nextInt(10_000_000));
            String base15    = CARD_BIN + mid;
            int    check     = luhnCheckDigit(base15);
            String candidate = base15 + check;
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

        // ── Industry limit check ───────────────────────────────────────────────
        long activeCardCount = cardRepository.findByAccountId(accountId)
                .stream()
                .filter(c -> c.getCardStatus() != CardStatus.CANCELLED
                        && c.getCardStatus() != CardStatus.EXPIRED)
                .count();

        if (activeCardCount >= MAX_CARDS_PER_ACCOUNT) {
            throw new BadRequestException(
                    "This account already has " + activeCardCount + " active card(s). " +
                            "Maximum allowed per account is " + MAX_CARDS_PER_ACCOUNT + ". " +
                            "Cancel or expire an existing card before issuing a new one.");
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
                LocalDate.now().plusYears(3)
        );
        card.setAccount(account);
        // PIN is intentionally NOT set here.
        // The customer sets their own PIN at the ATM kiosk after card issuance.
        Card saved = cardRepository.save(card);
        cardEmailNotificationService.sendCardIssuedEmail(
                account.getCustomer(),
                account,
                saved,
                activeCardCount > 0
        );
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardResponse> getCardsByAccount(Long accountId) {
        // Step 1: load the account from DB using the URL accountId
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found: " + accountId));

        // Step 2: get the JWT principal from Spring Security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Step 3: only check if caller is an ATM session (starts with ATM_)
        // Admin JWTs (subject = "admin") skip this check intentionally
        if (auth != null && auth.getName().startsWith("ATM_")) {

            // Step 4: build what the principal SHOULD be for this account
            // and compare against what it actually IS
            if (!auth.getName().equals("ATM_" + account.getAccountNumber())) {
                throw new BadRequestException("Forbidden: account mismatch");
            }
        }

        // So return card if account owns it
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
    public void sendCardEmail(Long accountId, Long cardId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Card not found: " + cardId));

        if (!card.getAccount().getAccountId().equals(accountId)) {
            throw new BadRequestException("Card does not belong to account " + accountId);
        }

        cardEmailNotificationService.sendCardIssuedEmail(
                account.getCustomer(),
                account,
                card,
                true
        );
    }

    @Override
    @Transactional
    public void cancelCard(Long accountId, Long cardId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new NotFoundException("Card not found: " + cardId));
        if (!card.getAccount().getAccountId().equals(account.getAccountId())) {
            throw new BadRequestException("Card does not belong to account " + accountId);
        }
        cardRepository.delete(card);
    }
}