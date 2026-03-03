package com.azizsattarov.corebanking.account;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.account.dto.CreateAccountRequest;

import java.math.BigDecimal;

public interface AccountService {
    AccountResponse createAccount(Long customerId, CreateAccountRequest request);
    void removeAccount(Long customerId, Long accountId);
}
