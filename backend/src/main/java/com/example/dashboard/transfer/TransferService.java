package com.example.dashboard.transfer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.dashboard.account.Account;
import com.example.dashboard.account.AccountStore;

/**
 * Coordinates money transfers for the POC. This method currently performs
 * only business validation; locking, balance mutation, ledger writes, and
 * idempotency handling are added in later steps.
 */
@Service
public class TransferService {

    private static final BigDecimal MAX_TRANSFER_AMOUNT = new BigDecimal("25000");
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(24);

    private final Map<String, Object> accountLocks = new ConcurrentHashMap<>();

    private final AccountStore accountStore;
    private final TransferStore transferStore;
    private final TransactionStore transactionStore;
    private final IdempotencyKeyStore idempotencyKeyStore;

    public TransferService(AccountStore accountStore,
                           TransferStore transferStore,
                           TransactionStore transactionStore,
                           IdempotencyKeyStore idempotencyKeyStore) {
        this.accountStore = accountStore;
        this.transferStore = transferStore;
        this.transactionStore = transactionStore;
        this.idempotencyKeyStore = idempotencyKeyStore;
    }

    public TransferResult createTransfer(String authenticatedUsername,
                                         String sourceAccountId,
                                         String idempotencyKey,
                                         TransferRequest request) {
        String requestHash = hashRequest(request);
        Optional<IdempotencyKey> existingKey = idempotencyKeyStore.find(idempotencyKey);
        if (existingKey.isPresent()) {
            if (!existingKey.get().requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException("Idempotency key already used with a different request");
            }
            Transfer existingTransfer = transferStore.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record exists but its transfer is missing"));
            return new TransferResult(existingTransfer, true);
        }

        Account source = accountStore.findById(sourceAccountId)
                .filter(account -> account.owner().equals(authenticatedUsername))
                .orElseThrow(() -> new AccountNotFoundException("Source account not found"));

        Account destination = accountStore.findById(request.destination())
                .orElseThrow(() -> new AccountNotFoundException("Destination account not found"));

        
        
        if (source.id().equals(destination.id())) {
            throw new TransferValidationException("Source and destination accounts must differ");
        }
        if (!"ACTIVE".equals(source.status())) {
            throw new TransferValidationException("Source account is not active");
        }
        if (!"ACTIVE".equals(destination.status())) {
            throw new TransferValidationException("Destination account is not active");
        }
        if (!source.currency().equals(destination.currency())) {
            throw new TransferValidationException("Source and destination currencies must match");
        }
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferValidationException("Amount must be greater than zero");
        }
        if (request.amount().compareTo(MAX_TRANSFER_AMOUNT) > 0) {
            throw new TransferValidationException("Amount exceeds the maximum transfer limit");
        }
        if (request.amount().scale() > 2) {
            throw new TransferValidationException("Amount cannot have more than two decimal places");
        }
        if (source.balance().compareTo(request.amount()) < 0) {
            throw new TransferValidationException("Insufficient balance");
        }

        String firstId = source.id().compareTo(destination.id()) <= 0 ? source.id() : destination.id();
        String secondId = firstId.equals(source.id()) ? destination.id() : source.id();
        Object firstLock = accountLocks.computeIfAbsent(firstId, k -> new Object());
        Object secondLock = accountLocks.computeIfAbsent(secondId, k -> new Object());

        Transfer transfer;
        synchronized (firstLock) {
            synchronized (secondLock) {
                Account lockedSource = accountStore.findById(sourceAccountId)
                        .orElseThrow(() -> new AccountNotFoundException("Source account not found"));
                Account lockedDestination = accountStore.findById(request.destination())
                        .orElseThrow(() -> new AccountNotFoundException("Destination account not found"));

                if (lockedSource.balance().compareTo(request.amount()) < 0) {
                    throw new TransferValidationException("Insufficient balance");
                }

                boolean sourceUpdated = false;
                boolean destinationUpdated = false;
                try {
                    accountStore.save(lockedSource.withBalance(lockedSource.balance().subtract(request.amount())));
                    sourceUpdated = true;
                    accountStore.save(lockedDestination.withBalance(lockedDestination.balance().add(request.amount())));
                    destinationUpdated = true;

                    Instant now = Instant.now();
                    transfer = new Transfer(
                            UUID.randomUUID().toString(),
                            lockedSource.id(),
                            lockedDestination.id(),
                            request.amount(),
                            lockedSource.currency(),
                            Transfer.Status.COMPLETED,
                            now,
                            idempotencyKey);
                    transferStore.save(transfer);

                    transactionStore.save(new Transaction(
                            UUID.randomUUID().toString(),
                            lockedSource.id(),
                            Transaction.Type.DEBIT,
                            request.amount(),
                            Transaction.Reason.TRANSFER_OUT,
                            transfer.id(),
                            now));
                    transactionStore.save(new Transaction(
                            UUID.randomUUID().toString(),
                            lockedDestination.id(),
                            Transaction.Type.CREDIT,
                            request.amount(),
                            Transaction.Reason.TRANSFER_IN,
                            transfer.id(),
                            now));
                } catch (Exception e) {
                    // Compensating rollback restores only the account balances. Transfer
                    // and Transaction writes are append-only in this POC and are not undone;
                    // a production system would mark the transfer FAILED and/or write
                    // reversing ledger entries instead.
                    if (destinationUpdated) {
                        accountStore.save(lockedDestination);
                    }
                    if (sourceUpdated) {
                        accountStore.save(lockedSource);
                    }
                    throw e;
                }
            }
        }

        Instant savedAt = Instant.now();
        idempotencyKeyStore.save(new IdempotencyKey(
                idempotencyKey,
                requestHash,
                null,
                savedAt,
                savedAt.plus(IDEMPOTENCY_RETENTION)));
        return new TransferResult(transfer, false);
    }

    /**
     * Looks up a transfer by ID. Visibility is restricted to the owner of either
     * the source or destination account; callers who own neither receive the same
     * 404 response as for a non-existent transfer, preventing existence-leakage of
     * other users' transfers. Returns an enriched {@link TransferDetailView} with
     * account information resolved so the frontend needs no additional lookups.
     */
    public TransferDetailView findById(String authenticatedUsername, String transferId) {
        Transfer transfer = transferStore.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found"));

        Account source = accountStore.findById(transfer.sourceAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transfer references missing source account: " + transfer.sourceAccountId()));
        Account destination = accountStore.findById(transfer.destinationAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "Transfer references missing destination account: " + transfer.destinationAccountId()));

        boolean userOwnsSource = source.owner().equals(authenticatedUsername);
        boolean userOwnsDestination = destination.owner().equals(authenticatedUsername);

        if (!userOwnsSource && !userOwnsDestination) {
            // Same response as 'not found' — don't leak existence of others' transfers
            throw new TransferNotFoundException("Transfer not found");
        }

        return new TransferDetailView(
                transfer.id(),
                transfer.amount(),
                transfer.currency(),
                transfer.status(),
                transfer.createdAt(),
                new TransferDetailView.AccountRef(source.id(), source.owner(), source.currency()),
                new TransferDetailView.AccountRef(destination.id(), destination.owner(), destination.currency()),
                transfer.idempotencyKey()
        );
    }

    private String hashRequest(TransferRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = request.destination() + ":" + request.amount().toPlainString();
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
