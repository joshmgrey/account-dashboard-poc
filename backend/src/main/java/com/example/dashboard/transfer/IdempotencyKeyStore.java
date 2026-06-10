package com.example.dashboard.transfer;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory idempotency record store for the POC. Expired entries are
 * removed lazily on read; nothing sweeps the map proactively, and all
 * keys are lost on restart. Replace with an indexed database table when
 * moving beyond the POC.
 */
@Component
public class IdempotencyKeyStore {

    private final Map<String, IdempotencyKey> recordsByKey = new ConcurrentHashMap<>();

    public Optional<IdempotencyKey> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        IdempotencyKey record = recordsByKey.get(key);
        if (record == null) {
            return Optional.empty();
        }
        if (record.expiresAt().isBefore(Instant.now())) {
            recordsByKey.remove(key, record);
            return Optional.empty();
        }
        return Optional.of(record);
    }

    public void save(IdempotencyKey record) {
        recordsByKey.put(record.key(), record);
    }
}
