package com.example.dashboard.transfer;

import java.math.BigDecimal;
import java.util.Map;
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

    public void createTransfer(String authenticatedUsername,
                               String sourceAccountId,
                               String idempotencyKey,
                               TransferRequest request) {
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

        synchronized (firstLock) {
            synchronized (secondLock) {
                Account lockedSource = accountStore.findById(sourceAccountId)
                        .orElseThrow(() -> new AccountNotFoundException("Source account not found"));
                Account lockedDestination = accountStore.findById(request.destination())
                        .orElseThrow(() -> new AccountNotFoundException("Destination account not found"));

                if (lockedSource.balance().compareTo(request.amount()) < 0) {
                    throw new TransferValidationException("Insufficient balance");
                }

                accountStore.save(lockedSource.withBalance(lockedSource.balance().subtract(request.amount())));
                accountStore.save(lockedDestination.withBalance(lockedDestination.balance().add(request.amount())));
            }
        }
    }
}
