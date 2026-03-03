package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.transaction.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionService {
    TransactionResponse deposit(Long accountId, DepositRequest depositRequest);
    TransactionResponse withdraw(Long accountId, WithdrawRequest withdrawRequest);
    TransferResponse transfer(Long accountFromId, TransferRequest transferRequest);
    List<TransactionResponse> getTransactionHistory(Long accountId);
}
