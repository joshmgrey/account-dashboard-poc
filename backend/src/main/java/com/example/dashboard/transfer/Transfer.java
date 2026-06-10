package com.example.dashboard.transfer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A money movement between two accounts. The ledger detail lives in the
 * two {@link Transaction} rows linked back here by {@code relatedTransferId};
 * {@code idempotencyKey} is recorded for traceability only — duplicate
 * prevention is handled by {@link IdempotencyKeyStore}.
 */
public record Transfer(
        String id,
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        String currency,
        Status status,
        Instant createdAt,
        String idempotencyKey
) {
    public enum Status {
        PENDING,
        COMPLETED,
        FAILED
    }
}
