package com.azizsattarov.corebanking.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    // Appended to the 17-digit BBAN payload (bank code + account digits)
    // to produce the final 18-digit BBAN.
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
    // Computes the two IBAN check digits CC in: DE CC <BBAN>
    // Steps per ISO 13616-1:
    //   1. Move country code + "00" to the end of the BBAN
    //   2. Replace each letter with its numeric equivalent (A=10, B=11, …)
    //   3. Compute 98 − (numeric string MOD 97)
    private static String ibanCheckDigits(String countryCode, String bban) {
        // Step 1: rearrange — BBAN + country letters + "00"
        String rearranged = bban + countryCode + "00";

        // Step 2: letters → digits
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numeric.append(Character.getNumericValue(c)); // A=10 … Z=35
            } else {
                numeric.append(c);
            }
        }

        // Step 3: MOD 97 on arbitrarily large number (process in chunks)
        java.math.BigInteger bigInt =
                new java.math.BigInteger(numeric.toString());
        int remainder = bigInt.mod(java.math.BigInteger.valueOf(97)).intValue();
        int checkValue = 98 - remainder;

        // Check digits are always two characters, zero-padded if needed
        return String.format("%02d", checkValue);
    }

    // ── Account number generation ─────────────────────────────────────────────
    // Produces a DE-format IBAN: DE<CC><BANK_CODE><9 random digits><Luhn>
    // BBAN = 8-digit bank code + 9 random digits + 1 Luhn digit = 18 digits
    // Full IBAN = "DE" + 2 check digits + 18-digit BBAN = 22 characters
    private String generateAccountNumber() {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {

            // 9 random individual account digits
            String accountPart = String.format(
                    "%0" + ACCOUNT_DIGITS + "d",
                    RANDOM.nextLong(1_000_000_000L) // 10^9
            );

            // 17-digit payload → append Luhn to get 18-digit BBAN
            String payload17 = BANK_CODE + accountPart;
            int    luhn      = luhnCheckDigit(payload17);
            String bban      = payload17 + luhn; // 18 digits

            // Two MOD-97 IBAN check digits
            String checkDigits = ibanCheckDigits(COUNTRY_CODE, bban);

            // Full IBAN
            String iban = COUNTRY_CODE + checkDigits + bban;

            if (!accountRepository.existsByAccountNumber(iban)) {
                return iban;
            }
        }
        throw new IllegalStateException(
                "Could not generate unique account number after " + MAX_RETRIES + " attempts");
    }

    // ── Service methods (unchanged logic) ─────────────────────────────────────

    @Override
    @Transactional
    public AccountResponse createAccount(Long customerId,
                                          CreateAccountRequest createAccountRequest) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException(
                        "Customer Not Found: " + customerId));

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
