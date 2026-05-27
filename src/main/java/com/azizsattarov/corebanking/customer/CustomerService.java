package com.azizsattarov.corebanking.customer;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.customer.dto.CreateCustomerRequest;
import com.azizsattarov.corebanking.customer.dto.CustomerResponse;
import com.azizsattarov.corebanking.customer.dto.UpdateCustomerRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CustomerService {
    CustomerResponse saveCustomer(CreateCustomerRequest request);
    Page<CustomerResponse> fetchCustomerList(Pageable pageable);
    List<AccountResponse> getAccountsByCustomer(Long customerId);
    CustomerResponse updateCustomer(Long customerId, UpdateCustomerRequest request);
    void deleteCustomerById(Long customerId);
}
