package com.example.dashboard.transfer;

import java.time.Instant;

/**
 * Record of a previously processed transfer request, looked up by the
 * client-supplied {@code Idempotency-Key} header. {@code requestHash}
 * detects key reuse with a different body (409); {@code responseBody}
 * is replayed verbatim on a matching retry (200).
 */
public record IdempotencyKey(
        String key,
        String requestHash,
        String responseBody,
        Instant createdAt,
        Instant expiresAt
) {
}
