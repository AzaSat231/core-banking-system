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

        // IIN/BIN of a bank system (Identification number that each bank has and consist of 8 digits)
        private static final String BANK_IIN = "62260012";
        private static final java.util.Random RANDOM = new java.util.Random();

        public AccountServiceImpl(AccountRepository accountRepository, CustomerRepository customerRepository) {
            this.accountRepository = accountRepository;
            this.customerRepository = customerRepository;
        }

        // Final, single-digit number appended to an identification sequence to verify its accuracy
        // Mathematical formula that was developed by IBM scientist Hans Peter Luhn
        private static int luhnCheckDigit(String number) {
            int sum = 0;
            boolean alternate = true;
            for (int i = number.length() - 1; i >= 0; i--) {
                int digit = Character.getNumericValue(number.charAt(i));
                if (alternate) {
                    digit *= 2;
                    if (digit > 9) digit -= 9;
                }
                sum += digit;
                alternate = !alternate;
            }
            return (10 - (sum % 10)) % 10;
        }

        private String generateAccountNumber() {
            String accountPart = String.format("%07d", RANDOM.nextInt(10_000_000)); // Individual Account Number - 7 digits
            String bankPlusAccount = BANK_IIN + accountPart;    // Combined parts of Account and IIN -15 digits
            int checkDigit = luhnCheckDigit(bankPlusAccount);   // Check Digit - 1 digit
            return bankPlusAccount + checkDigit;                // 16 digits
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
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new NotFoundException("Account Not Found: " + accountId));

            if (!account.getCustomer().getCustomerId().equals(customerId)){
                throw new BadRequestException("Account does not belong to this Customer");
            }

            if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw new BadRequestException("Cannot close account with remaining balance");
            }

            account.setDeletedAt(LocalDateTime.now()); // soft delete
            account.setAccountStatus(AccountStatus.CLOSED);
            accountRepository.save(account);
        }
}
