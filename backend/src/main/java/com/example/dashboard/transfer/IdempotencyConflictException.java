package com.example.dashboard.transfer;

/**
 * Thrown when an idempotency key has already been used with a different
 * request body. Maps to HTTP 409.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
