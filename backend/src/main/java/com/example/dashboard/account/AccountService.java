package com.example.dashboard.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * In-memory account data source for the POC. Replace with a real
 * repository / persistence layer when wiring up a database.
 */
@Service
public class AccountService {

    private final List<Account> accounts = List.of(
            new Account("ACC-1001", "Operating Checking", "CHECKING", "USD",
                    new BigDecimal("128450.72"), "ACTIVE"),
            new Account("ACC-1002", "Payroll", "CHECKING", "USD",
                    new BigDecimal("54320.10"), "ACTIVE"),
            new Account("ACC-1003", "Reserve Savings", "SAVINGS", "USD",
                    new BigDecimal("982100.00"), "ACTIVE"),
            new Account("ACC-1004", "EUR Trade", "CHECKING", "EUR",
                    new BigDecimal("17600.45"), "FROZEN")
    );

    public List<Account> findAll() {
        return accounts;
    }

    public Optional<Account> findById(String id) {
        return accounts.stream()
                .filter(account -> account.id().equals(id))
                .findFirst();
    }
}
