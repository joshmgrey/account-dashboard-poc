package com.example.dashboard.account;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Owner-scoped account queries for the dashboard API. Lookups are scoped by
 * owner so a user can only ever see their own data; storage lives in
 * {@link AccountStore}. The sole exception is {@link #findDirectory()}, a
 * cross-user query that exposes only sanitized fields for the transfer
 * destination picker.
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

    /**
     * The one intentionally cross-user query: lists every active account as a
     * sanitized {@link AccountDirectoryEntry} (no balance or status) so users
     * can pick a transfer destination they don't own. Inactive accounts are
     * excluded so they can't be selected as a destination.
     */
    public List<AccountDirectoryEntry> findDirectory() {
        return accountStore.findAll().stream()
                .filter(account -> "ACTIVE".equals(account.status()))
                .map(AccountDirectoryEntry::from)
                .toList();
    }
}
