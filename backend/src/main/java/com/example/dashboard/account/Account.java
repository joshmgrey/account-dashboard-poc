package com.example.dashboard.account;

import java.math.BigDecimal;

/**
 * Lightweight account representation returned by the dashboard API.
 * {@code owner} is the username the account belongs to and is never
 * serialized to clients.
 */
public record Account(
        String id,
        String owner,
        String name,
        String type,
        String currency,
        BigDecimal balance,
        String status
) {
}
