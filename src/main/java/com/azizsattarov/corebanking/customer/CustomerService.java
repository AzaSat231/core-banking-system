package com.azizsattarov.corebanking.customer;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.customer.dto.CreateCustomerRequest;
import com.azizsattarov.corebanking.customer.dto.CustomerResponse;
import com.azizsattarov.corebanking.customer.dto.UpdateCustomerRequest;

import java.util.List;

public interface CustomerService {
    CustomerResponse saveCustomer(CreateCustomerRequest request);
    List<CustomerResponse> fetchCustomerList();
    List<AccountResponse> getAccountsByCustomer(Long customerId);
    CustomerResponse updateCustomer(Long customerId, UpdateCustomerRequest request);
    void deleteCustomerById(Long customerId);
}
