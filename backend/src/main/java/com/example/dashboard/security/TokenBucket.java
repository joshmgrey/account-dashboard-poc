package com.example.dashboard.security;

/**
 * A classic token-bucket: starts full, refills continuously at a fixed rate
 * and is capped at {@code capacity}. Each allowed request consumes one token.
 * Thread-safe via coarse synchronization, which is sufficient for the modest
 * contention of a per-key bucket.
 */
final class TokenBucket {

    private final double capacity;
    private final double refillTokensPerMilli;

    private double tokens;
    private long lastRefillMillis;

    TokenBucket(long capacity, double refillTokensPerMinute) {
        this.capacity = capacity;
        this.refillTokensPerMilli = refillTokensPerMinute / 60_000.0;
        this.tokens = capacity;
        this.lastRefillMillis = System.currentTimeMillis();
    }

    synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    synchronized long lastSeenMillis() {
        return lastRefillMillis;
    }

    private void refill() {
        long now = System.currentTimeMillis();
        double elapsed = now - lastRefillMillis;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed * refillTokensPerMilli);
            lastRefillMillis = now;
        }
    }
}
