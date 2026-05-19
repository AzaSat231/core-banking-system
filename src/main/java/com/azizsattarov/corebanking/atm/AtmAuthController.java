package com.azizsattarov.corebanking.atm;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.auth.JwtUtil;
import com.azizsattarov.corebanking.customer.Customer;
import com.azizsattarov.corebanking.customer.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/atm")
public class AtmAuthController {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AtmAuthController(AccountRepository accountRepository,
                             CustomerRepository customerRepository,
                             JwtUtil jwtUtil,
                             PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> atmLogin(@RequestBody Map<String, String> body) {
        String accountNumber = body.get("accountNumber");
        String pin = body.get("pin");

        if (accountNumber == null || pin == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "accountNumber and pin are required"));
        }

        Account account = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (account == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid account number or PIN"));
        }
        if (!account.isActive()) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Account is " + account.getAccountStatus()));
        }

        // Changed to Accounts having multiple PINs
        Customer customer = account.getCustomer();
        if (customer == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid account number or PIN"));
        }
        if (account.getPinHash() == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "PIN not set. Contact a branch."));
        }
        if (!passwordEncoder.matches(pin, account.getPinHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid account number or PIN"));
        }

        var userDetails = org.springframework.security.core.userdetails.User
                .withUsername("ATM_" + accountNumber)
                .password("")
                .authorities("ROLE_USER")
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "accountId", account.getAccountId(),
                "accountNumber", accountNumber,
                "customerName", customer.getFirstName() + " " + customer.getLastName(),
                "balance", account.getBalance()
        ));
    }

    @PostMapping("/set-pin")
    public ResponseEntity<?> setPin(@RequestBody Map<String, String> body) {
        String accountIdStr = body.get("accountId");
        String pin = body.get("pin");

        if (accountIdStr == null || pin == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "accountId and pin are required"));
        }
        if (pin.length() < 4) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "PIN must be at least 4 digits"));
        }

        Account account = accountRepository.findById(Long.parseLong(accountIdStr)).orElse(null);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        account.setPinHash(passwordEncoder.encode(pin));
        accountRepository.save(account);

        return ResponseEntity.ok(Map.of("message", "PIN set for account " + accountIdStr));
    }
}