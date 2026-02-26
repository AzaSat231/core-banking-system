package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.transaction.dto.TransactionResponse;
import com.azizsattarov.corebanking.transaction.dto.TransferResponse;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionService {
    TransactionResponse deposit(Long accountId, BigDecimal amountDeposit);
    TransactionResponse withdraw(Long accountId, BigDecimal amountWithdraw);
    TransferResponse transfer(Long accountFromId, Long accountToId, BigDecimal amountTransfer);
    List<TransactionResponse> getTransactionHistory(Long accountId);
}
