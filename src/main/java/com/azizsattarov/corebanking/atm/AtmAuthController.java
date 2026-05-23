package com.azizsattarov.corebanking.atm;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.auth.JwtUtil;
import com.azizsattarov.corebanking.card.Card;
import com.azizsattarov.corebanking.card.CardRepository;
import com.azizsattarov.corebanking.card.CardStatus;
import com.azizsattarov.corebanking.customer.Customer;
import com.azizsattarov.corebanking.customer.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/atm")
public class AtmAuthController {

    private final CardRepository cardRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AtmAuthController(CardRepository cardRepository,
                             JwtUtil jwtUtil,
                             PasswordEncoder passwordEncoder) {
        this.cardRepository = cardRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Resolve a card number to its associated account number — no PIN required.
     *
     * Called by the middleware BEFORE the lockout check so lockouts can be keyed
     * by accountNumber rather than cardNumber.  One customer may have several
     * cards; all cards for the same account share a single lockout counter.
     *
     * Returns:
     *   200  { accountNumber: "..." }  — card exists and is not CANCELLED
     *   404  { error: "..." }          — card not found
     *   403  { error: "..." }          — card is CANCELLED (treat same as not found
     *                                    from the ATM's perspective)
     *
     * Security: this endpoint reveals only the account number that the card
     * belongs to.  The account number is already printed on receipts and known
     * to the card holder, so no sensitive data is exposed.  The endpoint is
     * intentionally unauthenticated so the middleware can call it before the
     * customer enters their PIN.
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
                    .body(Map.of("error", "PIN not set for this card. Contact a branch."));
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

    // ── Set PIN ───────────────────────────────────────────────────────────────

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

    // ── Reset PIN ─────────────────────────────────────────────────────────────

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
}