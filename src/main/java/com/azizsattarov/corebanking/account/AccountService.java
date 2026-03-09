package com.azizsattarov.corebanking.account;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.account.dto.CreateAccountRequest;
import com.azizsattarov.corebanking.account.dto.UpdateAccountRequest;

public interface AccountService {
    AccountResponse createAccount(Long customerId, CreateAccountRequest createAccountRequest);
    AccountResponse changeStatus(Long accountId, UpdateAccountRequest updateAccountRequest);
    void removeAccount(Long customerId, Long accountId);
}
