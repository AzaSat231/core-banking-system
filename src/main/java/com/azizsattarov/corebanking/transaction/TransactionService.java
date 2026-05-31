package com.azizsattarov.corebanking.transaction;

import com.azizsattarov.corebanking.transaction.dto.*;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionService {
    TransactionResponse deposit(Long accountId, DepositRequest depositRequest, String requestKey);
    default TransactionResponse deposit(Long accountId, DepositRequest depositRequest) {
        return deposit(accountId, depositRequest, null);
    }

    default TransactionResponse withdraw(Long accountId, WithdrawRequest withdrawRequest) {
        return withdraw(accountId, withdrawRequest, null, null);
    }
    default TransactionResponse withdraw(Long accountId,
                                         WithdrawRequest withdrawRequest,
                                         Integer ackTimeoutSeconds) {
        return withdraw(accountId, withdrawRequest, ackTimeoutSeconds, null);
    }

    TransactionResponse withdraw(Long accountId,
                                 WithdrawRequest withdrawRequest,
                                 Integer ackTimeoutSeconds,
                                 String requestKey);
    TransferResponse transfer(Long accountFromId, TransferRequest transferRequest);
    List<TransactionResponse> getTransactionHistory(Long accountId);

    TransactionResponse confirmDispense(Long accountId, Long transactionId);

    int reverseExpiredPendingDispenses();
}
