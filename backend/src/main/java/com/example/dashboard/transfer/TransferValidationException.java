package com.example.dashboard.transfer;

/**
 * Thrown when transfer input fails business validation (e.g. non-positive
 * amount, insufficient balance, mismatched currency). Maps to HTTP 422.
 */
public class TransferValidationException extends RuntimeException {
    public TransferValidationException(String message) {
        super(message);
    }
}
