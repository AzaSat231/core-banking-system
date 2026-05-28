package com.azizsattarov.corebanking.card;

import com.azizsattarov.corebanking.card.dto.CardResponse;
import com.azizsattarov.corebanking.card.dto.IssueCardRequest;
import com.azizsattarov.corebanking.card.dto.UpdateCardRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Card management endpoints.
 * Scoped under accounts so the accountId is always verifiable.
 *
 * POST   /accounts/{accountId}/cards              — issue a new card
 * GET    /accounts/{accountId}/cards              — list cards for an account
 * PATCH  /accounts/{accountId}/cards/{cardId}/status — block / unblock / expire
 * DELETE /accounts/{accountId}/cards/{cardId}     — cancel card
 */
@RestController
@RequestMapping("/accounts/{accountId}/cards")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping
    public ResponseEntity<CardResponse> issueCard(
            @PathVariable Long accountId,
            @Valid @RequestBody(required = false) IssueCardRequest request) {
        if (request == null) request = new IssueCardRequest(null);
        CardResponse card = cardService.issueCard(accountId, request);
        return ResponseEntity.status(201).body(card);
    }

    @GetMapping
    public List<CardResponse> getCards(@PathVariable Long accountId) {
        return cardService.getCardsByAccount(accountId);
    }

    @PatchMapping("/{cardId}/status")
    public ResponseEntity<CardResponse> updateStatus(
            @PathVariable Long accountId,
            @PathVariable Long cardId,
            @Valid @RequestBody UpdateCardRequest request) {
        return ResponseEntity.ok(cardService.updateCardStatus(cardId, request));
    }

    @PostMapping("/{cardId}/send-email")
    public ResponseEntity<Map<String, String>> sendCardEmail(
            @PathVariable Long accountId,
            @PathVariable Long cardId) {
        cardService.sendCardEmail(accountId, cardId);
        return ResponseEntity.ok(Map.of("message", "Card email sent."));
    }

    @DeleteMapping("/{cardId}")
    public ResponseEntity<Void> cancelCard(
            @PathVariable Long accountId,
            @PathVariable Long cardId) {
        cardService.cancelCard(accountId, cardId);
        return ResponseEntity.noContent().build();
    }
}