package com.example.dashboard.transfer;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory ledger for the POC. Append-only: each transfer saves two
 * entries (debit and credit) linked by {@code relatedTransferId}. All
 * entries are lost on restart, so this cannot serve as a real audit
 * log; production needs a persistent Transaction table.
 */
@Component
public class TransactionStore {

    private final Map<String, Transaction> transactionsById = new ConcurrentHashMap<>();

    public void save(Transaction transaction) {
        transactionsById.put(transaction.id(), transaction);
    }

    public Optional<Transaction> findById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(transactionsById.get(id));
    }

    public List<Transaction> findByAccountId(String accountId) {
        return transactionsById.values().stream()
                .filter(transaction -> transaction.accountId().equals(accountId))
                .sorted(Comparator.comparing(Transaction::createdAt))
                .toList();
    }

    public List<Transaction> findByTransferId(String transferId) {
        return transactionsById.values().stream()
                .filter(transaction -> transferId.equals(transaction.relatedTransferId()))
                .sorted(Comparator.comparing(Transaction::createdAt))
                .toList();
    }
}
