package com.azizsattarov.corebanking.card;

import com.azizsattarov.corebanking.card.dto.CardResponse;
import com.azizsattarov.corebanking.card.dto.IssueCardRequest;
import com.azizsattarov.corebanking.card.dto.UpdateCardRequest;

import java.util.List;

public interface CardService {
    CardResponse issueCard(Long accountId, IssueCardRequest request);
    List<CardResponse> getCardsByAccount(Long accountId);
    CardResponse updateCardStatus(Long cardId, UpdateCardRequest request);
    void sendCardEmail(Long accountId, Long cardId);
    void cancelCard(Long accountId, Long cardId);
}