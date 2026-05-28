package com.azizsattarov.corebanking.atm;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.auth.JwtUtil;
import com.azizsattarov.corebanking.card.Card;
import com.azizsattarov.corebanking.card.CardRepository;
import com.azizsattarov.corebanking.card.CardService;
import com.azizsattarov.corebanking.card.CardStatus;
import com.azizsattarov.corebanking.card.dto.IssueCardRequest;
import com.azizsattarov.corebanking.customer.Customer;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/atm")
public class AtmAuthController {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final CardService cardService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AtmAuthController(CardRepository cardRepository,
                             AccountRepository accountRepository,
                             CardService cardService,
                             JwtUtil jwtUtil,
                             PasswordEncoder passwordEncoder) {
        this.cardRepository    = cardRepository;
        this.accountRepository = accountRepository;
        this.cardService       = cardService;
        this.jwtUtil           = jwtUtil;
        this.passwordEncoder   = passwordEncoder;
    }

    /**
     * Resolve a card number to its associated account number — no PIN required.
     * Called by the middleware BEFORE the lockout check.
     */
    @GetMapping("/resolve-card")
    public ResponseEntity<?> resolveCard(@RequestParam String cardNumber) {
        Card card = cardRepository.findByCardNumber(cardNumber).orElse(null);

        if (card == null) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "Card not found"));
        }
        if (card.getCardStatus() == CardStatus.CANCELLED) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Card is CANCELLED"));
        }

        return ResponseEntity.ok(Map.of(
                "accountNumber", card.getAccount().getAccountNumber()
        ));
    }

    // ── Self-service card creation ─────────────────────────────────────────────

    /**
     * Step 1 of self-service card setup (called by ATM after customer enters account number).
     *
     * The customer has NO card yet — they visit the ATM, enter their account number,
     * and this endpoint issues a card for them automatically. The card has NO PIN set.
     * The customer must call /atm/set-own-pin next to activate it.
     *
     * Security:
     *   - Requires the account to exist and be ACTIVE
     *   - Does NOT require an existing card or PIN (this is first-time setup)
     *   - Does NOT return the full card number — returns only the masked number
     *     and the cardId needed for PIN setup, so no sensitive data is exposed
     *     over this unauthenticated channel
     *   - Accessible only via ROLE_SERVICE (middleware) — never called directly
     *     by the ATM UI; the middleware proxies it and rate-limits the caller
     *
     * Returns:
     *   201  { cardId, maskedCardNumber, accountNumber, customerName }
     *   400  { error }  — account not active or card limit reached
     *   404  { error }  — account not found
     */
    @PostMapping("/create-card-for-account")
    public ResponseEntity<?> createCardForAccount(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(409).body(Map.of(
                "error", "ATM flow no longer creates cards. Ask admin to issue card, then set PIN with account number + card number."
        ));
    }

    /**
     * Step 2 of self-service card setup — customer sets their own PIN.
     *
     * Called after /atm/create-card-for-account returns a cardId.
     * The customer enters the PIN twice; the ATM UI confirms they match before
     * calling this endpoint.
     *
     * This endpoint is intentionally separate from /atm/set-pin (admin use only).
     * It is gated by ROLE_SERVICE so the middleware controls access.
     *
     * After this call the card is fully activated and the customer can log in
     * using their card number + PIN.
     */
    @PostMapping("/set-own-pin")
    public ResponseEntity<?> setOwnPin(@RequestBody Map<String, String> body) {
        String cardIdStr = body.get("cardId");
        String pin       = body.get("pin");
        String accountNumber = body.get("accountNumber");

        if (cardIdStr == null || pin == null || accountNumber == null || accountNumber.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "cardId, pin, and accountNumber are required"));
        }
        if (!pin.matches("\\d{4}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "PIN must be exactly 4 digits"));
        }

        Card card = cardRepository.findById(Long.parseLong(cardIdStr)).orElse(null);
        if (card == null) return ResponseEntity.notFound().build();

        if (!accountNumber.equalsIgnoreCase(card.getAccount().getAccountNumber())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Account number does not match this card"));
        }

        // Safety check: only allow setting PIN if no PIN is set yet (first-time setup).
        // For PIN reset after admin unlock, the customer uses /atm/reset-pin instead.
        if (card.getPinHash() != null) {
            return ResponseEntity.status(400)
                    .body(Map.of("error",
                            "PIN is already set for this card. Use the reset flow instead."));
        }

        if (!card.getAccount().isActive()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Account is " + card.getAccount().getAccountStatus()));
        }

        card.setPinHash(passwordEncoder.encode(pin));
        cardRepository.save(card);

        return ResponseEntity.ok(Map.of(
                "message",        "PIN set successfully. Your card is now active.",
                "maskedNumber",   "**** **** **** " + card.getCardNumber().substring(12),
                "accountNumber",  card.getAccount().getAccountNumber()
        ));
    }

    /**
     * Prepare PIN setup for an already issued card.
     * Prevents accidental new-card creation when the customer already received a card number by email.
     */
    @PostMapping("/prepare-own-pin")
    public ResponseEntity<?> prepareOwnPin(@RequestBody Map<String, String> body) {
        String accountNumber = (body.get("accountNumber") == null ? "" : body.get("accountNumber")).trim();

        if (accountNumber.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "accountNumber is required"));
        }

        Account account = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (account == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Account not found"));
        }
        if (!account.isActive()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Account is " + account.getAccountStatus()));
        }

        Card card = cardRepository.findByAccountId(account.getAccountId()).stream()
                .filter(c -> c.getCardStatus() != CardStatus.CANCELLED)
                .filter(c -> c.getCardStatus() != CardStatus.EXPIRED)
                .filter(c -> !c.getExpiryDate().isBefore(LocalDate.now()))
                .filter(c -> c.getPinHash() == null)
                .findFirst()
                .orElse(null);

        if (card == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "No card pending PIN setup found for this account. Ask admin to issue a card."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "cardId", card.getCardId(),
                "maskedNumber", "**** **** **** " + card.getCardNumber().substring(12),
                "holderInitials", toInitials(card.getHolderName()),
                "accountNumber", account.getAccountNumber(),
                "message", "Card found. Set your PIN to activate it."
        ));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> atmLogin(@RequestBody Map<String, String> body) {
        String cardNumber = body.get("cardNumber");
        String pin        = body.get("pin");

        if (cardNumber == null || pin == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "cardNumber and pin are required"));
        }
        if (!pin.matches("\\d{4}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "PIN must be exactly 4 digits"));
        }

        Card card = cardRepository.findByCardNumber(cardNumber).orElse(null);

        if (card == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid card number or PIN"));
        }
        if (card.getCardStatus() == CardStatus.BLOCKED) {
            return ResponseEntity.status(403).body(Map.of("error", "Card is BLOCKED"));
        }
        if (card.getCardStatus() == CardStatus.EXPIRED
                || card.getExpiryDate().isBefore(LocalDate.now())) {
            return ResponseEntity.status(403).body(Map.of("error", "Card is EXPIRED"));
        }
        if (card.getCardStatus() == CardStatus.CANCELLED) {
            return ResponseEntity.status(403).body(Map.of("error", "Card is CANCELLED"));
        }

        Account account = card.getAccount();
        if (!account.isActive()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Account is " + account.getAccountStatus()));
        }
        if (card.getPinHash() == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "PIN not set for this card. Please visit an ATM to set your PIN."));
        }
        if (!passwordEncoder.matches(pin, card.getPinHash())) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid card number or PIN"));
        }

        Customer customer = account.getCustomer();
        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername("ATM_" + account.getAccountNumber())
                .password("")
                .authorities("ROLE_USER")
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(Map.of(
                "token",         token,
                "cardId",        card.getCardId(),
                "accountId",     account.getAccountId(),
                "accountNumber", account.getAccountNumber(),
                "customerName",  customer.getFirstName() + " " + customer.getLastName(),
                "balance",       account.getBalance()
        ));
    }

    // ── Admin: Set PIN (ROLE_ADMIN) ────────────────────────────────────────────
    // Kept for admin branch operations (e.g. issuing a card at a physical branch).

    @PostMapping("/set-pin")
    public ResponseEntity<?> setPin(@RequestBody Map<String, String> body) {
        String cardIdStr = body.get("cardId");
        String pin       = body.get("pin");

        if (cardIdStr == null || pin == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "cardId and pin are required"));
        }
        if (!pin.matches("\\d{4}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "PIN must be exactly 4 digits"));
        }

        Card card = cardRepository.findById(Long.parseLong(cardIdStr)).orElse(null);
        if (card == null) return ResponseEntity.notFound().build();

        card.setPinHash(passwordEncoder.encode(pin));
        cardRepository.save(card);

        return ResponseEntity.ok(Map.of("message", "PIN set for card " + card.getCardNumber()));
    }

    // ── Reset PIN (ROLE_SERVICE — middleware after admin unlock) ──────────────

    @PostMapping("/reset-pin")
    public ResponseEntity<?> resetPin(@RequestBody Map<String, String> body) {
        String cardNumber = body.get("cardNumber");
        String pin        = body.get("pin");

        if (cardNumber == null || pin == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "cardNumber and pin are required"));
        }
        if (!pin.matches("\\d{4}")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "PIN must be exactly 4 digits"));
        }

        Card card = cardRepository.findByCardNumber(cardNumber).orElse(null);
        if (card == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Card not found"));
        }

        Account account = card.getAccount();
        if (!account.isActive()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Account is " + account.getAccountStatus()));
        }

        card.setPinHash(passwordEncoder.encode(pin));
        cardRepository.save(card);

        return ResponseEntity.ok(Map.of("message", "PIN reset for card " + cardNumber));
    }

    private static String toInitials(String holderName) {
        if (holderName == null || holderName.isBlank()) {
            return "";
        }
        return Arrays.stream(holderName.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase())
                .limit(2)
                .reduce("", String::concat);
    }
}