package com.example.dashboard.transfer;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory idempotency record store for the POC. An expired entry is
 * replaced only when its key is reserved again; nothing sweeps the map
 * proactively, and all keys are lost on restart. Replace with an indexed
 * database table when moving beyond the POC.
 */
@Component
public class IdempotencyKeyStore {

    private final Map<String, IdempotencyKey> recordsByKey = new ConcurrentHashMap<>();

    /**
     * Atomically claims {@code record.key()} for a new request. Returns empty
     * if the claim succeeded, or the unexpired record already holding the key.
     * The check and the insert happen in one {@code compute} call, so two
     * concurrent requests with the same key can never both win the claim.
     */
    public Optional<IdempotencyKey> reserve(IdempotencyKey record) {
        IdempotencyKey[] existing = new IdempotencyKey[1];
        recordsByKey.compute(record.key(), (key, current) -> {
            if (current != null && !current.expiresAt().isBefore(Instant.now())) {
                existing[0] = current;
                return current;
            }
            return record;
        });
        return Optional.ofNullable(existing[0]);
    }

    /**
     * Drops a reservation, but only if it is still the record held for its key.
     */
    public void release(IdempotencyKey record) {
        recordsByKey.remove(record.key(), record);
    }

    public void save(IdempotencyKey record) {
        recordsByKey.put(record.key(), record);
    }

    /**
     * Wipes the store's contents. Test-only — provided for integration tests
     * that need a clean slate between cases. Do not call from production code.
     */
    public void clearForTest() {
        recordsByKey.clear();
    }
}
