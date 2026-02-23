package com.azizsattarov.corebanking.customer;

import java.util.List;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.dto.AccountResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
public class CustomerController {
    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<Customer> saveCustomer(@Valid @RequestBody Customer customer){
        Customer created = customerService.saveCustomer(customer);
        return ResponseEntity.status(201).body(created);   // 201 - Created
    }

    @GetMapping
    public List<Customer> fetchDepartmentList() {
        return customerService.fetchCustomerList();
    }

    @GetMapping("/{customerId}/accounts")
    public List<AccountResponse> getAccountsByCustomer(@PathVariable Long customerId){return customerService.getAccountsByCustomer(customerId); }

    @PutMapping("/{id}")
    public Customer updateCustomer(@RequestBody Customer customer, @PathVariable("id") Long customerId) {
        return customerService.updateCustomer(customer, customerId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomerById(@PathVariable("id") Long customerId) {
        customerService.deleteCustomerById(customerId);
        return ResponseEntity.noContent().build();
    }
}
