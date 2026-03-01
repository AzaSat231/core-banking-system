package com.azizsattarov.corebanking.customer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.customer.dto.CreateCustomerRequest;
import com.azizsattarov.corebanking.customer.dto.CustomerResponse;
import com.azizsattarov.corebanking.customer.dto.UpdateCustomerRequest;
import com.azizsattarov.corebanking.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerServiceImpl implements CustomerService{
    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
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
    public List<CustomerResponse> fetchCustomerList() {
        return customerRepository.findAll()
                .stream()
                .map(customer -> new CustomerResponse(
                        customer.getCustomerId(),
                        customer.getFirstName(),
                        customer.getLastName(),
                        customer.getNationalId(),
                        customer.getEmail(),
                        customer.getPhoneNumber(),
                        customer.getDateOfBirth(),
                        customer.getCreatedAt()
                ))
                .toList();
    }

    @Override
    public List<AccountResponse> getAccountsByCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));

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

        customerRepository.delete(customer);
    }
}
