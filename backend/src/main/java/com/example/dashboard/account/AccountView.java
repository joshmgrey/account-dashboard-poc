package com.example.dashboard.account;

import java.math.BigDecimal;

/**
 * Client-facing account projection. Excludes the internal {@code owner}
 * field so ownership details are never exposed over the API.
 */
public record AccountView(
        String id,
        String name,
        String type,
        String currency,
        BigDecimal balance,
        String status
) {
    public static AccountView from(Account account) {
        return new AccountView(
                account.id(),
                account.name(),
                account.type(),
                account.currency(),
                account.balance(),
                account.status());
    }
}
