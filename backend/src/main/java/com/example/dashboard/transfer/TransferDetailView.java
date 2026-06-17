package com.example.dashboard.transfer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Enriched view returned by GET /api/transfers/{id}. Account information is
 * resolved server-side so the frontend doesn't need additional lookups.
 */
public record TransferDetailView(
        String id,
        BigDecimal amount,
        String currency,
        Transfer.Status status,
        Instant createdAt,
        AccountRef source,
        AccountRef destination,
        String idempotencyKey
) {
    /**
     * Lightweight pointer to an account. Balance and status are intentionally
     * excluded for the same privacy reasoning as AccountDirectoryEntry.
     */
    public record AccountRef(
            String id,
            String owner,
            String currency
    ) {}
}
