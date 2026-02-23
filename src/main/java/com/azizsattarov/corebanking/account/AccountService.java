package com.azizsattarov.corebanking.account;

import com.azizsattarov.corebanking.account.dto.AccountResponse;

import java.math.BigDecimal;

public interface AccountService {
    AccountResponse createAccount(Long customerId, String accountNumber, BigDecimal initialBalance);
    void removeAccount(Long customerId, Long accountId);
}
