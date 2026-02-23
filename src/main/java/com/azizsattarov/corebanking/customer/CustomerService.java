package com.azizsattarov.corebanking.customer;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import java.util.List;

public interface CustomerService {
    Customer saveCustomer(Customer customer);
    List<Customer> fetchCustomerList();
    List<AccountResponse> getAccountsByCustomer(Long customerId);
    Customer updateCustomer(Customer customer, Long customerId);
    void deleteCustomerById(Long customerId);
}
