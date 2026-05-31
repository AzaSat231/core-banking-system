package com.azizsattarov;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.customer.Customer;
import com.azizsattarov.corebanking.customer.CustomerRepository;
import com.azizsattarov.corebanking.customer.CustomerStatus;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.transaction.TransactionService;
import com.azizsattarov.corebanking.transaction.dto.DepositRequest;
import com.azizsattarov.corebanking.transaction.dto.TransactionResponse;
import com.azizsattarov.corebanking.transaction.dto.TransferRequest;
import com.azizsattarov.corebanking.transaction.dto.WithdrawRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@Transactional  // rolls back after each test → clean DB every time
class TransactionServiceTest extends BaseIntegrationTest {

    @Autowired private TransactionService transactionService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CustomerRepository customerRepository;

    private Account account;
    private Account accountB;

    // Runs before each test — creates fresh test data
    @BeforeEach
    void setUp() {
        Customer customer = new Customer();
        customer.setFirstName("John");
        customer.setLastName("Doe");
        customer.setNationalId("12345678");
        customer.setEmail("john@test.com");
        customer.setPhoneNumber("1234567890");
        customer.setDateOfBirth(LocalDate.of(1990, 1, 1));
        customer.setCustomerStatus(CustomerStatus.ACTIVE);
        customerRepository.save(customer);

        account = new Account("ACC001", new BigDecimal("1000.00"));
        customer.addAccount(account);
        accountRepository.save(account);

        accountB = new Account("ACC002", new BigDecimal("500.00"));
        customer.addAccount(accountB);
        accountRepository.save(accountB);
    }

    // TEST 1: Deposit
    @Test
    void deposit_shouldIncreaseBalance() {
        // ARRANGE
        DepositRequest request = new DepositRequest(new BigDecimal("200.00"));

        // ACT
        transactionService.deposit(account.getAccountId(), request);

        // ASSERT
        Account updated = accountRepository.findById(account.getAccountId()).get();
        assertThat(updated.getBalance()).isEqualByComparingTo("1200.00");
    }

    // TEST 2: Withdraw with insufficient funds
    @Test
    void withdraw_shouldThrow_whenInsufficientFunds() {
        // ARRANGE - try to withdraw more than balance
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("9999.00"));

        // ACT & ASSERT - expect exception
        assertThatThrownBy(() ->
                transactionService.withdraw(account.getAccountId(), request)
        )
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Withdraw Amount must be less than Balance Amount");
    }

    // TEST: Idempotent deposit — a retry carrying the same request key must NOT
    // apply the balance change twice (closes the lost-response/double-charge gap).
    @Test
    void deposit_shouldBeIdempotent_whenSameRequestKey() {
        DepositRequest request = new DepositRequest(new BigDecimal("200.00"));
        String key = "idem-deposit-001";

        TransactionResponse first  = transactionService.deposit(account.getAccountId(), request, key);
        TransactionResponse second = transactionService.deposit(account.getAccountId(), request, key);

        // Same original transaction is replayed, not a new one.
        assertThat(second.transactionId()).isEqualTo(first.transactionId());
        assertThat(second.referenceId()).isEqualTo(first.referenceId());

        // Balance moved only once: 1000 + 200, not + 400.
        Account updated = accountRepository.findById(account.getAccountId()).get();
        assertThat(updated.getBalance()).isEqualByComparingTo("1200.00");
    }

    // TEST: Idempotent withdraw — same key replays the original withdrawal.
    @Test
    void withdraw_shouldBeIdempotent_whenSameRequestKey() {
        WithdrawRequest request = new WithdrawRequest(new BigDecimal("300.00"));
        String key = "idem-withdraw-001";

        TransactionResponse first  = transactionService.withdraw(account.getAccountId(), request, null, key);
        TransactionResponse second = transactionService.withdraw(account.getAccountId(), request, null, key);

        assertThat(second.transactionId()).isEqualTo(first.transactionId());

        // Debited only once: 1000 - 300, not - 600.
        Account updated = accountRepository.findById(account.getAccountId()).get();
        assertThat(updated.getBalance()).isEqualByComparingTo("700.00");
    }

    // TEST 3: Transfer
    @Test
    void transfer_shouldMoveMoney_betweenAccounts() {
        // ARRANGE
        TransferRequest request = new TransferRequest(
                accountB.getAccountId(),
                new BigDecimal("300.00")
        );

        // ACT
        transactionService.transfer(account.getAccountId(), request);

        // ASSERT
        Account updatedA = accountRepository.findById(account.getAccountId()).get();
        Account updatedB = accountRepository.findById(accountB.getAccountId()).get();

        assertThat(updatedA.getBalance()).isEqualByComparingTo("700.00");
        assertThat(updatedB.getBalance()).isEqualByComparingTo("800.00");
    }
}
