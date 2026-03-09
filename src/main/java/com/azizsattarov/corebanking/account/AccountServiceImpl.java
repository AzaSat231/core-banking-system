package com.azizsattarov.corebanking.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.account.dto.CreateAccountRequest;
import com.azizsattarov.corebanking.account.dto.UpdateAccountRequest;
import com.azizsattarov.corebanking.customer.Customer;
import com.azizsattarov.corebanking.customer.CustomerRepository;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
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

    private String generateAccountNumber() {
        return "ACC" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    @Override
    @Transactional
    public AccountResponse createAccount(Long customerId, CreateAccountRequest createAccountRequest){
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer Not Found: " + customerId));

        if (createAccountRequest.initialBalance().compareTo(BigDecimal.ZERO) < 0){
            throw new BadRequestException("Balance cannot be negative");
        }

        Account account = new Account(generateAccountNumber(), createAccountRequest.initialBalance());

        customer.addAccount(account);

        Account saved = accountRepository.save(account);

        return new AccountResponse(
                saved.getAccountId(),
                saved.getAccountNumber(),
                saved.getAccountStatus(),
                saved.getBalance(),
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public AccountResponse changeStatus(Long accountId, UpdateAccountRequest updateAccountRequest) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        account.setAccountStatus(updateAccountRequest.accountStatus());
        Account saved = accountRepository.save(account);

        return new AccountResponse(
                saved.getAccountId(),
                saved.getAccountNumber(),
                saved.getAccountStatus(),
                saved.getBalance(),
                saved.getCreatedAt()
        );
    }


    @Override
    @Transactional
    public void removeAccount(Long customerId, Long accountId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer Not Found: " + customerId));

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

        if (!account.getCustomer().getCustomerId().equals(customerId)){
            throw new BadRequestException("Account does not belong to this Customer");
        }

        account.setDeletedAt(LocalDateTime.now()); // soft delete
        account.setAccountStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
    }
}
