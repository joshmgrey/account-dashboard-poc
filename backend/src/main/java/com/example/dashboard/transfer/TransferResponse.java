package com.example.dashboard.transfer;

/**
 * Success payload for a transfer request: the resulting {@link Transfer} and a
 * human-readable message describing the outcome.
 */
public record TransferResponse(
        Transfer transfer,
        String message
) {
}
