package com.azizsattarov.corebanking.customer;

import java.util.List;

import com.azizsattarov.corebanking.account.dto.AccountResponse;
import com.azizsattarov.corebanking.customer.dto.CreateCustomerRequest;
import com.azizsattarov.corebanking.customer.dto.CustomerResponse;
import com.azizsattarov.corebanking.customer.dto.UpdateCustomerRequest;
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
    public ResponseEntity<CustomerResponse> saveCustomer(@Valid @RequestBody CreateCustomerRequest request){
        CustomerResponse created = customerService.saveCustomer(request);
        return ResponseEntity.status(201).body(created);   // 201 - Created
    }

    @GetMapping
    public List<CustomerResponse> fetchDepartmentList() {
        return customerService.fetchCustomerList();
    }

    @GetMapping("/{customerId}/accounts")
    public List<AccountResponse> getAccountsByCustomer(@PathVariable Long customerId){return customerService.getAccountsByCustomer(customerId); }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable("id") Long customerId, @Valid @RequestBody UpdateCustomerRequest request) {
        CustomerResponse updated = customerService.updateCustomer(customerId, request);
        return ResponseEntity.status(200).body(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomerById(@PathVariable("id") Long customerId) {
        customerService.deleteCustomerById(customerId);
        return ResponseEntity.noContent().build();
    }
}
