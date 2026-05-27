package com.azizsattarov.corebanking.customer;

import java.time.LocalDateTime;
import java.util.List;

import com.azizsattarov.corebanking.account.AccountStatus;
import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.customer.dto.CreateCustomerRequest;
import com.azizsattarov.corebanking.customer.dto.CustomerResponse;
import com.azizsattarov.corebanking.customer.dto.UpdateCustomerRequest;
import com.azizsattarov.corebanking.exception.BadRequestException;
import com.azizsattarov.corebanking.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerServiceImpl implements CustomerService{
    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }


    private void assertCustomerOwnership(Long customerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;
        String principal = auth.getName();
        if (!principal.startsWith("ATM_")) return;
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));
        boolean owns = customer.getAccounts().stream()
                .anyMatch(a -> ("ATM_" + a.getAccountNumber()).equals(principal));
        if (!owns) throw new BadRequestException("Forbidden: account mismatch");
    }

    @Override
    public CustomerResponse saveCustomer(CreateCustomerRequest request) {
        Customer customer = new Customer();
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setNationalId(request.nationalId());
        customer.setEmail(request.email());
        customer.setPhoneNumber(request.phoneNumber());
        customer.setDateOfBirth(request.dateOfBirth());
        customer.setCustomerStatus(CustomerStatus.ACTIVE);

        Customer saved = customerRepository.save(customer);

        return new CustomerResponse(
                saved.getCustomerId(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getNationalId(),
                saved.getEmail(),
                saved.getPhoneNumber(),
                saved.getDateOfBirth(),
                saved.getCreatedAt()
        );
    }

    @Override
    public Page<CustomerResponse> fetchCustomerList(Pageable pageable) {
        return customerRepository.findAll(pageable)
                .map(customer -> new CustomerResponse(
                        customer.getCustomerId(),
                        customer.getFirstName(),
                        customer.getLastName(),
                        customer.getNationalId(),
                        customer.getEmail(),
                        customer.getPhoneNumber(),
                        customer.getDateOfBirth(),
                        customer.getCreatedAt()
                ));
    }

    @Override
    public List<AccountResponse> getAccountsByCustomer(Long customerId) {

        // Check if accessible to the customer number or not
        assertCustomerOwnership(customerId);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

        return customer.getAccounts().stream()
                .map(a -> new AccountResponse(
                        a.getAccountId(),
                        a.getAccountNumber(),
                        a.getAccountStatus(),
                        a.getBalance(),
                        a.getCreatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional
    public CustomerResponse updateCustomer(Long customerId, UpdateCustomerRequest request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setEmail(request.email());
        customer.setPhoneNumber(request.phoneNumber());

        Customer saved = customerRepository.save(customer);
        return new CustomerResponse(
                saved.getCustomerId(),
                saved.getFirstName(),
                saved.getLastName(),
                saved.getNationalId(),
                saved.getEmail(),
                saved.getPhoneNumber(),
                saved.getDateOfBirth(),
                saved.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public void deleteCustomerById(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

        LocalDateTime now = LocalDateTime.now();

        // Soft-delete all accounts explicitly (cascade won't do this for you)
        customer.getAccounts().forEach(account -> {
            account.setDeletedAt(now);
            account.setAccountStatus(AccountStatus.CLOSED);
        });

        customer.setDeletedAt(now);
        customer.setCustomerStatus(CustomerStatus.CLOSED);
        customerRepository.save(customer);
    }
}
