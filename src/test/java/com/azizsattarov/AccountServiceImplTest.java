package com.azizsattarov;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.account.AccountServiceImpl;
import com.azizsattarov.corebanking.account.AccountStatus;
import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.account.dto.CreateAccountRequest;
import com.azizsattarov.corebanking.account.dto.UpdateAccountRequest;
import com.azizsattarov.corebanking.customer.Customer;
import com.azizsattarov.corebanking.customer.CustomerRepository;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountServiceImpl: account-limit policy, balance validation,
 * IBAN generation correctness (Luhn + MOD97-10), and close-account rules.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    AccountRepository accountRepository;
    @Mock CustomerRepository customerRepository;
    @InjectMocks
    AccountServiceImpl service;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setCustomerId(1L);
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setAccounts(new HashSet<>());
    }

    // ── Account creation: happy path + generated IBAN validity ─────────────────

    @Test
    void createAccount_returnsValidGermanIban() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse resp = service.createAccount(1L, new CreateAccountRequest(BigDecimal.ZERO));

        String iban = resp.accountNumber();
        assertNotNull(iban);
        assertEquals(22, iban.length(), "German IBAN must be 22 characters");
        assertTrue(iban.startsWith("DE"));
        assertTrue(isValidIbanMod97(iban), "IBAN must satisfy ISO 7064 MOD97-10");
        assertTrue(isValidLuhn(iban.substring(4)), "BBAN payload must end in a valid Luhn digit");
    }

    @Test
    void createAccount_rejectsNegativeInitialBalance() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        assertThrows(BadRequestException.class,
                () -> service.createAccount(1L, new CreateAccountRequest(new BigDecimal("-1"))));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createAccount_unknownCustomer_throwsNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class,
                () -> service.createAccount(99L, new CreateAccountRequest(BigDecimal.ZERO)));
    }

    // ── Account-per-customer limit (policy ceiling = 5) ────────────────────────

    @Test
    void createAccount_blocksSixthActiveAccount() {
        Set<Account> existing = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            Account a = new Account("DE" + i, BigDecimal.ZERO);
            a.setAccountStatus(AccountStatus.ACTIVE);
            existing.add(a);
        }
        customer.setAccounts(existing);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.createAccount(1L, new CreateAccountRequest(BigDecimal.ZERO)));
        assertTrue(ex.getMessage().contains("Maximum allowed"));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createAccount_closedAccountsDoNotCountTowardLimit() {
        Set<Account> existing = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            Account a = new Account("DE" + i, BigDecimal.ZERO);
            a.setAccountStatus(AccountStatus.CLOSED);   // closed ⇒ frees a slot
            existing.add(a);
        }
        customer.setAccounts(existing);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.createAccount(1L, new CreateAccountRequest(BigDecimal.ZERO)));
    }

    // ── Status change ──────────────────────────────────────────────────────────

    @Test
    void changeStatus_updatesAccountStatus() {
        Account acc = new Account("DE123", BigDecimal.TEN);
        acc.setAccountStatus(AccountStatus.ACTIVE);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(acc));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse resp = service.changeStatus(10L, new UpdateAccountRequest(AccountStatus.FROZEN));
        assertEquals(AccountStatus.FROZEN, resp.accountStatus());
    }

    // ── Close account rules ──────────────────────────────────────────────────--

    @Test
    void removeAccount_rejectsNonZeroBalance() {
        Account acc = new Account("DE123", new BigDecimal("50.00"));
        acc.setCustomer(customer);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(acc));
        assertThrows(BadRequestException.class, () -> service.removeAccount(1L, 10L));
    }

    @Test
    void removeAccount_rejectsWrongCustomer() {
        Customer other = new Customer();
        other.setCustomerId(2L);
        Account acc = new Account("DE123", BigDecimal.ZERO);
        acc.setCustomer(other);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(acc));
        assertThrows(BadRequestException.class, () -> service.removeAccount(1L, 10L));
    }

    @Test
    void removeAccount_softDeletesWhenBalanceZero() {
        Account acc = new Account("DE123", BigDecimal.ZERO);
        acc.setCustomer(customer);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(acc));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.removeAccount(1L, 10L);

        assertEquals(AccountStatus.CLOSED, acc.getAccountStatus());
        assertNotNull(acc.getDeletedAt());
    }


    // ── Helpers: independent validators (do NOT reuse production code) ─────────

    /** Validate IBAN via ISO 7064 MOD97-10: move first 4 chars to the end,
     *  convert letters A=10..Z=35, the integer mod 97 must equal 1. */
    private static boolean isValidIbanMod97(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isLetter(c)) {
                numeric.append(Character.getNumericValue(c)); // A=10, B=11, …
            } else {
                numeric.append(c); // digit char — StringBuilder appends correctly
            }
        }
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }

    /**
     * Standard Luhn validation (ISO/IEC 7812-1).
     * Doubles every second digit from the RIGHT, starting with the second-to-last.
     * The impl computes the check digit the same way, so conventions match.
     */
    private static boolean isValidLuhn(String number) {
        int sum = 0;
        boolean doubleIt = false; // rightmost digit is NOT doubled — it's the check digit
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = Character.getNumericValue(number.charAt(i));
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
