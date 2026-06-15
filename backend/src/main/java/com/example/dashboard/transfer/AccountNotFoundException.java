package com.example.dashboard.transfer;

/**
 * Thrown when a referenced account does not exist or is not owned by the
 * authenticated user. Maps to HTTP 404.
 */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
