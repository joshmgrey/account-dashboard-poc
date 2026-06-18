package com.example.dashboard.transfer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory transfer store for the POC. Entries are lost on restart;
 * production would persist these in a Transfer table.
 */
@Component
public class TransferStore {

    private final Map<String, Transfer> transfersById = new ConcurrentHashMap<>();

    public void save(Transfer transfer) {
        transfersById.put(transfer.id(), transfer);
    }

    public Optional<Transfer> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(transfersById.get(id));
    }

    public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return transfersById.values().stream()
                .filter(transfer -> idempotencyKey.equals(transfer.idempotencyKey()))
                .findFirst();
    }

    /**
     * Wipes the store's contents. Test-only — provided for integration tests
     * that need a clean slate between cases. Do not call from production code.
     */
    public void clearForTest() {
        transfersById.clear();
    }
}
