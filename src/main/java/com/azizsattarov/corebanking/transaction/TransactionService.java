package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.transaction.dto.TransactionResponse;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionService {
    TransactionResponse deposit(Long accountId, BigDecimal amountDeposit);
    TransactionResponse withdraw(Long accountId, BigDecimal amountWithdraw);
    List<TransactionResponse> getTransactionHistory(Long accountId);
}
