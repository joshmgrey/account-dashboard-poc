package com.example.dashboard.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * In-memory account data source for the POC. Accounts are owned by a specific
 * user and all lookups are scoped by owner so a user can only ever see their
 * own data. Replace with a real persistence layer when wiring up a database.
 */
@Service
public class AccountService {

    private final List<Account> accounts = List.of(
            new Account("ACC-1001", "alice", "Operating Checking", "CHECKING", "USD",
                    new BigDecimal("128450.72"), "ACTIVE"),
            new Account("ACC-1002", "alice", "Payroll", "CHECKING", "USD",
                    new BigDecimal("54320.10"), "ACTIVE"),
            new Account("ACC-1003", "alice", "Reserve Savings", "SAVINGS", "USD",
                    new BigDecimal("982100.00"), "ACTIVE"),
            new Account("ACC-2001", "bob", "Personal Checking", "CHECKING", "USD",
                    new BigDecimal("8420.55"), "ACTIVE"),
            new Account("ACC-2002", "bob", "Travel Savings", "SAVINGS", "EUR",
                    new BigDecimal("17600.45"), "FROZEN")
    );

    public List<AccountView> findForOwner(String owner) {
        return accounts.stream()
                .filter(account -> account.owner().equals(owner))
                .map(AccountView::from)
                .toList();
    }

    public Optional<AccountView> findForOwnerById(String owner, String id) {
        return accounts.stream()
                .filter(account -> account.owner().equals(owner) && account.id().equals(id))
                .map(AccountView::from)
                .findFirst();
    }
}
