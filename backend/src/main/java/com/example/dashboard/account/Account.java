package com.example.dashboard.account;

import java.math.BigDecimal;

/**
 * Lightweight account representation returned by the dashboard API.
 */
public record Account(
        String id,
        String name,
        String type,
        String currency,
        BigDecimal balance,
        String status
) {
}
