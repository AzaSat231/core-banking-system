package com.azizsattarov.corebanking.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    // ── Industry limits ────────────────────────────────────────────────────────
    // Based on standard retail banking policy (EU / German market):
    //   - Most retail banks allow 3–5 accounts per customer (current, savings, etc.)
    //   - We set 5 as the ceiling; exceptions require admin override.
    private static final int MAX_ACCOUNTS_PER_CUSTOMER = 5;

    // ISO 13616 — Germany (DE), BBAN structure: 8!n 10!n, total IBAN length 22
    // Bank identifier (Bankleitzahl): 8 digits, fixed per institution
    private static final String COUNTRY_CODE  = "DE";
    private static final String BANK_CODE     = "62260099"; // 8-digit bank identifier
    private static final int    ACCOUNT_DIGITS = 9;         // random individual account digits
    private static final int    MAX_RETRIES    = 10;
    private static final java.util.Random RANDOM = new java.util.Random();

    public AccountServiceImpl(AccountRepository accountRepository,
                              CustomerRepository customerRepository) {
        this.accountRepository  = accountRepository;
        this.customerRepository = customerRepository;
    }

    // ── Luhn check digit (ISO/IEC 7812-1) ────────────────────────────────────
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

    // ── MOD-97 check digits (ISO/IEC 7064 MOD97-10) ──────────────────────────
    private static String ibanCheckDigits(String countryCode, String bban) {
        String rearranged = bban + countryCode + "00";
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numeric.append(Character.getNumericValue(c));
            } else {
                numeric.append(c);
            }
        }
        java.math.BigInteger bigInt =
                new java.math.BigInteger(numeric.toString());
        int remainder = bigInt.mod(java.math.BigInteger.valueOf(97)).intValue();
        int checkValue = 98 - remainder;
        return String.format("%02d", checkValue);
    }

    // ── Account number generation ─────────────────────────────────────────────
    private String generateAccountNumber() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String accountPart = String.format(
                    "%0" + ACCOUNT_DIGITS + "d",
                    RANDOM.nextLong(1_000_000_000L)
            );
            String payload17 = BANK_CODE + accountPart;
            int    luhn      = luhnCheckDigit(payload17);
            String bban      = payload17 + luhn;
            String checkDigits = ibanCheckDigits(COUNTRY_CODE, bban);
            String iban = COUNTRY_CODE + checkDigits + bban;
            if (!accountRepository.existsByAccountNumber(iban)) {
                return iban;
            }
        }
        throw new IllegalStateException(
                "Could not generate unique account number after " + MAX_RETRIES + " attempts");
    }

    // ── Service methods ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AccountResponse createAccount(Long customerId,
                                         CreateAccountRequest createAccountRequest) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException(
                        "Customer Not Found: " + customerId));

        // ── Industry limit check ───────────────────────────────────────────────
        long activeAccountCount = customer.getAccounts().stream()
                .filter(a -> a.getAccountStatus() != AccountStatus.CLOSED)
                .count();

        if (activeAccountCount >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw new BadRequestException(
                    "Customer already has " + activeAccountCount + " active account(s). " +
                            "Maximum allowed per customer is " + MAX_ACCOUNTS_PER_CUSTOMER + ". " +
                            "Please close an existing account before opening a new one.");
        }

        if (createAccountRequest.initialBalance()
                .compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Balance cannot be negative");
        }

        Account account = new Account(
                generateAccountNumber(),
                createAccountRequest.initialBalance());

        customer.addAccount(account);
        Account saved = accountRepository.save(account);

        return new AccountResponse(
                saved.getAccountId(),
                saved.getAccountNumber(),
                saved.getAccountStatus(),
                saved.getBalance(),
                saved.getCreatedAt());
    }

    @Override
    @Transactional
    public AccountResponse changeStatus(Long accountId,
                                        UpdateAccountRequest updateAccountRequest) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(
                        "Account Not Found: " + accountId));

        account.setAccountStatus(updateAccountRequest.accountStatus());
        Account saved = accountRepository.save(account);

        return new AccountResponse(
                saved.getAccountId(),
                saved.getAccountNumber(),
                saved.getAccountStatus(),
                saved.getBalance(),
                saved.getCreatedAt());
    }

    @Override
    @Transactional
    public void removeAccount(Long customerId, Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException(
                        "Account Not Found: " + accountId));

        if (!account.getCustomer().getCustomerId().equals(customerId)) {
            throw new BadRequestException(
                    "Account does not belong to this Customer");
        }

        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BadRequestException(
                    "Cannot close account with remaining balance");
        }

        account.setDeletedAt(LocalDateTime.now());
        account.setAccountStatus(AccountStatus.CLOSED);
        accountRepository.save(account);
    }
}
