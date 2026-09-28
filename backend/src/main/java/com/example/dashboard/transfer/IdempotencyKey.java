package com.example.dashboard.transfer;

import java.time.Instant;

/**
 * Record of a transfer request, looked up by the client-supplied
 * {@code Idempotency-Key} header. {@code requestHash} detects key reuse with
 * a different body (409); {@code responseBody} is replayed verbatim on a
 * matching retry (200). {@code status} is {@code IN_PROGRESS} from the moment
 * the key is reserved until the transfer commits, so concurrent duplicates
 * can be rejected instead of executing a second time.
 */
public record IdempotencyKey(
        String key,
        String requestHash,
        String responseBody,
        Instant createdAt,
        Instant expiresAt,
        Status status
) {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    public IdempotencyKey completed() {
        return new IdempotencyKey(key, requestHash, responseBody, createdAt, expiresAt, Status.COMPLETED);
    }
}
