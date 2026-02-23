package com.azizsattarov.corebanking.account;

import java.math.BigDecimal;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.customer.Customer;
import com.azizsattarov.corebanking.customer.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService{
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountServiceImpl(AccountRepository accountRepository, CustomerRepository customerRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public AccountResponse createAccount(Long customerId, String accountNumber, BigDecimal initialBalance){
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer Not Found"));

        if (initialBalance.compareTo(BigDecimal.ZERO) < 0){
            throw new IllegalArgumentException("Balance cannot be negative");
        }

        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setBalance(initialBalance);

        customer.addAccount(account);

        Account saved = accountRepository.save(account);

        return new AccountResponse(
                saved.getAccountId(),
                saved.getAccountNumber(),
                saved.getBalance(),
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public void removeAccount(Long customerId, Long accountId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer Not Found"));

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account Not Found"));

        if (!account.getCustomer().getCustomerId().equals(customerId)){
            throw new IllegalArgumentException("Account does not belong to this Customer");
        }

        customer.removeAccount(account);

    }
}
