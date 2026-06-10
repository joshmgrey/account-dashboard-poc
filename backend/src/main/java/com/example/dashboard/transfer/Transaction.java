package com.example.dashboard.transfer;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Single ledger entry. Each transfer writes two rows linked by
 * {@code relatedTransferId}: a {@code DEBIT} on the source account and a
 * {@code CREDIT} on the destination.
 */
public record Transaction(
        String id,
        String accountId,
        Type type,
        BigDecimal amount,
        Reason reason,
        String relatedTransferId,
        Instant createdAt
) {
    public enum Type {
        DEBIT,
        CREDIT
    }

    public enum Reason {
        TRANSFER_IN,
        TRANSFER_OUT
    }
}
