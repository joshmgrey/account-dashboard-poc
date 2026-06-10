package com.example.dashboard.account;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory account store for the POC, seeded with demo accounts. Balances
 * are mutable via {@link #save}, unlike the rest of the seed data. Replace
 * with a real repository when wiring up a database.
 */
@Component
public class AccountStore {

    private final Map<String, Account> accountsById = new ConcurrentHashMap<>();

    public AccountStore() {
        save(new Account("ACC-1001", "alice", "Operating Checking", "CHECKING", "USD",
                new BigDecimal("128450.72"), "ACTIVE"));
        save(new Account("ACC-1002", "alice", "Payroll", "CHECKING", "USD",
                new BigDecimal("54320.10"), "ACTIVE"));
        save(new Account("ACC-1003", "alice", "Reserve Savings", "SAVINGS", "USD",
                new BigDecimal("982100.00"), "ACTIVE"));
        save(new Account("ACC-2001", "bob", "Personal Checking", "CHECKING", "USD",
                new BigDecimal("8420.55"), "ACTIVE"));
        save(new Account("ACC-2002", "bob", "Travel Savings", "SAVINGS", "EUR",
                new BigDecimal("17600.45"), "FROZEN"));
    }

    public void save(Account account) {
        accountsById.put(account.id(), account);
    }

    public Optional<Account> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsById.get(id));
    }

    public List<Account> findAll() {
        return accountsById.values().stream()
                .sorted(Comparator.comparing(Account::id))
                .toList();
    }
}
