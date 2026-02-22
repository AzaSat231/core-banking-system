package com.azizsattarov.corebanking.customer;

import com.azizsattarov.corebanking.customer.Customer;
import java.util.List;

public interface CustomerService {
    Customer saveCustomer(Customer customer);
    List<Customer> fetchCustomerList();
    Customer updateCustomer(Customer customer, Long customerId);
    void deleteCustomerById(Long customerId);
}
