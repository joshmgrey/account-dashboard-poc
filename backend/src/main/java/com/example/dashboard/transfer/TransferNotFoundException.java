package com.example.dashboard.transfer;

/**
 * Thrown when a referenced transfer does not exist or is not visible to the
 * authenticated user. Maps to HTTP 404.
 */
public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(String message) {
        super(message);
    }
}
