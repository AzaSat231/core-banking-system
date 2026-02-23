package com.azizsattarov.corebanking.account;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {
    Account createAccount(Long customerId, String accountNumber, BigDecimal initialBalance);
    void removeAccount(Long customerId, Long accountId);
}
