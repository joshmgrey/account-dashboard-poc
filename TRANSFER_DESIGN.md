# Transfer Endpoint Design

## 1. API Specification

**Endpoint:** `POST /api/accounts/{id}/transfers`

**Headers:**
- `Idempotency-Key` (required)

**Request body:**
```json
{
  "destination": "string",
  "amount": "BigDecimal"
}
```

**Success response (201 Created — new transfer) OR (200 OK — idempotency replay):**
```json
{
  "transfer": {
    "id": "string",
    "amount": "BigDecimal",
    "sourceId": "string",
    "source": "AccountView",
    "destinationId": "string",
    "destination": "AccountView",
    "status": "string",
    "createdAt": "string",
  },
  "message": "string"
}
```

**Error response (any non-2xx):**
```json
{ "message": "string" }
```

**Status codes:**
- `201` — new transfer created
- `200` — idempotency key already exists with matching request
- `401` — not logged in or session expired mid-request
- `404` — source account doesn't exist OR isn't owned by the user (consistent with IDOR-prevention in GET /api/accounts/{id})
- `409` — idempotency key already used with a different request body
- `422` — input validation failed
- `500` — database down or pessimistic lock timeout


## 2. Decisions on the Six Concerns

### Authorization
External transfers (to accounts not owned by the user) are allowed by account ID. Known limitations for POC: no payee verification (real banks require the recipient to be added as a verified payee first), no notification to the destination account holder, no reversal mechanism, and account-ID-based addressing means a malicious user could probe for valid IDs. These are all out of scope for the POC.

### Input Validation
Valid input would be having the amount > 0 and amount < 25000, source account must be owned by the authenticated user, the currencies must be the same, destination must be active and exist, source must be active and exist, source and destination are not the same, amount is limited to 2 decimal places, and balance >= amount.  I chose 25000 as the max, as well as no overdrafting because 25000 is the max transfer amount and usually banks have overdraft fees.

### Atomicity
If debit succeeds, but credit fails, the money will disappear, unless there is a rollback.  This can be done with @Transactional, which will go in the service method.  The rollback behavior would be triggered when there is an unexpected error that happens after the input validation, since input validation would be checked before any operations are made.

### Idempotency
If the user clicks submit twice, they could see that they have transferred double what they were originally going to transfer.  I think using Idempotency keys would be a good thing to add in scope of the POC because of the fact that there might be instances where a user submits twice and we don't want them having to transfer double the money of what they originally wanted to transfer. The Idempotency-Key should go in the header.  This key should be stored in its own table that is indexed on the key.  Make sure to wrap the transactions and the idempotency table insert in one transaction.  Make sure to utilize a retention policy based on the risk profile.  We should then append it to the audit log for traceability without depending on it for duplicate prevention.  This ensures transaction integrity, performance, and regulatory adherence while still retaining a complete audit trail.

### Audit Logging
For the POC: one Transaction table (ledger view) with fields: id, account_id, type (DEBIT/CREDIT), amount, reason (TRANSFER_IN/TRANSFER_OUT/etc.), related_transfer_id, created_at. Each transfer creates two Transaction rows (one debit, one credit), both linked to the same Transfer.
For production: a separate AuditLog table for security/compliance, recording: user, action, source, destination, amount, outcome (success/failure type), status, idempotency_key, timestamp. The ledger answers 'what is my balance and how did it get here?' The audit log answers 'who did what when and was it successful?' Both are needed in real banking.

### Concurrency
If 2 transfers hit the same account simultaneously, there could be a chance that it could lead the account balance to be negative.  We can fix this by using Pessimistic locking.  Pessimistic locking acquires a row-level lock on the source account at the start of the transaction, blocking other writes until the transaction commits. This is appropriate for banking transfers where conflict is common (concurrent transfers on the same account are a real scenario) and correctness matters more than throughput. The alternative is optimistic locking (version checking + retry on conflict), which is faster under low conflict but adds retry complexity and risks lost updates if not implemented carefully.

## 3. Data Model

- **Transfer entity:** id, sourceAccountId, destinationAccountId, amount, currency, status (`PENDING`/`COMPLETED`/`FAILED`), createdAt, idempotencyKey
- **Transaction entity (ledger):** id, accountId, type (`DEBIT`/`CREDIT`), amount, reason, relatedTransferId, createdAt
- **IdempotencyKey entity:** key, requestHash, responseBody, createdAt, expiresAt
- **Relationships:** Transfer → Account (`source`, `destination`), Transaction → Account, Transaction → Transfer (optional)

## 4. POC vs Production Implementation

### Atomicity (replacing @Transactional)
In production, @Transactional groups multiple operations so that they either succeed together or roll back.  In-memory data structures, such as hash maps, do not have transactions like databases do.  One reason why in-memory is more error prone compared to database transactions is because you actually have to do the rollback manually.  This is one reason real systems prefer database transactions.

### Lock Ordering
Two transfers running concurrently, one going from X to Y, the other from Y to X.  If each thread locks its source first and then the destination, you would get a cycle.  Thread 1 holds X waiting for Y and Thread 2 holds Y waiting for X, causing a deadlock.  The fix is to lock in a consistent order, like locking the smaller ID first.  Both threads now agree on which lock to grab first.  Compared to in-memory, databases have deadlock detection, which aborts a transaction whenever two transactions are in a deadlock.  This is a real concern for the in-memory POC implementation but disappears when migrated to a database, another reason production systems use database transactions rather than manual in-memory equivalents.

### Idempotency
The in-memory idempotency store uses a ConcurrentHashMap keyed by the idempotency key.  It does the same job as the database table in production, fast lookup of an idempotency record by key.  Expiry is naive because stale entries are removed lazily on read but not proactively swept.  Without proactive clean-up, the map grows unbounded over time, which is a memory exhaustion concern.  All keys can also be lost whenever the JVM restarts.  This is fine for a POC because there will most likely be low traffic and a short-lived process, but production would need database-backed storage with proactive cleanup.

### Audit Logging
The data structure for in-memory audit logging is either a ConcurrentHashMap, keyed by transaction ID, or a List,  both store the transaction. For each transfer, there are two entries in the data structure, a debit on the source account and credit on the destination account linked to the same transfer ID.  The key limitation is that everything is lost on restart, which is worse for logging than idempotency because of the fact that losing the audit log means you cannot prove that the transaction happened.  This is acceptable for the POC because the project is not subject to banking compliance regulations, but in production audit log persistence would be non-negotiable.

## 5. Out of Scope

- Transfers between different currencies
- Deposit and withdrawal endpoints
- account transactions retrieval endpoint
- Automation of interest and fees