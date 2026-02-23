package com.azizsattarov.corebanking.customer;

import java.util.List;
import java.util.Objects;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import org.springframework.stereotype.Service;

@Service
public class CustomerServiceImpl implements CustomerService{
    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    @Override
    public List<Customer> fetchCustomerList() {
        return customerRepository.findAll();
    }

    @Override
    public List<AccountResponse> getAccountsByCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));

        return customer.getAccounts().stream()
                .map(a -> new AccountResponse(
                        a.getAccountId(),
                        a.getAccountNumber(),
                        a.getBalance(),
                        a.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public Customer updateCustomer(Customer customer, Long customerId) {
        Customer cusDB = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));

        if (Objects.nonNull(customer.getFirstName()) && !customer.getFirstName().isBlank()) {
            cusDB.setFirstName(customer.getFirstName());
        }
        if (Objects.nonNull(customer.getLastName()) && !customer.getLastName().isBlank()) {
            cusDB.setLastName(customer.getLastName());
        }

        return customerRepository.save(cusDB);
    }

    @Override
    public void deleteCustomerById(Long customerId) {
        customerRepository.deleteById(customerId);
    }
}
