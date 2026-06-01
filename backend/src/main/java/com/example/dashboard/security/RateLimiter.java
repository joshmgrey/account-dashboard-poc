package com.example.dashboard.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.dashboard.config.AppProperties;

/**
 * Per-key token-bucket rate limiter with separate policies for general traffic
 * and authentication endpoints. Keys are typically client IP addresses.
 *
 * <p>The backing maps are bounded: once a map exceeds {@link #MAX_KEYS} the
 * oldest-seen entries are evicted. This prevents the limiter itself from
 * becoming a memory-exhaustion (DoS) vector under a flood of unique keys.
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS = 50_000;

    private final Map<String, TokenBucket> generalBuckets = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> authBuckets = new ConcurrentHashMap<>();

    private final long generalCapacity;
    private final long generalRefillPerMinute;
    private final long authCapacity;
    private final long authRefillPerMinute;

    public RateLimiter(AppProperties properties) {
        AppProperties.RateLimit cfg = properties.getRatelimit();
        this.generalCapacity = cfg.getCapacity();
        this.generalRefillPerMinute = cfg.getRefillPerMinute();
        this.authCapacity = cfg.getAuthCapacity();
        this.authRefillPerMinute = cfg.getAuthRefillPerMinute();
    }

    public boolean allowRequest(String key) {
        return consume(generalBuckets, key, generalCapacity, generalRefillPerMinute);
    }

    public boolean allowAuthRequest(String key) {
        return consume(authBuckets, key, authCapacity, authRefillPerMinute);
    }

    private boolean consume(Map<String, TokenBucket> buckets, String key,
                            long capacity, long refillPerMinute) {
        evictIfNeeded(buckets);
        TokenBucket bucket = buckets.computeIfAbsent(key,
                ignored -> new TokenBucket(capacity, refillPerMinute));
        return bucket.tryConsume();
    }

    private void evictIfNeeded(Map<String, TokenBucket> buckets) {
        if (buckets.size() < MAX_KEYS) {
            return;
        }
        buckets.entrySet().stream()
                .min((a, b) -> Long.compare(a.getValue().lastSeenMillis(),
                        b.getValue().lastSeenMillis()))
                .map(Map.Entry::getKey)
                .ifPresent(buckets::remove);
    }
}
