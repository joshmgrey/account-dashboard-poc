package com.example.dashboard.account;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Owner-scoped account queries for the dashboard API. All lookups are
 * scoped by owner so a user can only ever see their own data; storage
 * lives in {@link AccountStore}.
 */
@Service
public class AccountService {

    private final AccountStore accountStore;

    public AccountService(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    public List<AccountView> findForOwner(String owner) {
        return accountStore.findAll().stream()
                .filter(account -> account.owner().equals(owner))
                .map(AccountView::from)
                .toList();
    }

    public Optional<AccountView> findForOwnerById(String owner, String id) {
        return accountStore.findById(id)
                .filter(account -> account.owner().equals(owner))
                .map(AccountView::from);
    }
}
