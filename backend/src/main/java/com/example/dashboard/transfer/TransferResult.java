package com.example.dashboard.transfer;

/**
 * Outcome of a transfer request: the resulting {@link Transfer} and whether it
 * was served from an idempotency replay ({@code isReplay} true) rather than a
 * newly executed transfer. Lets the controller distinguish a 200 replay from a
 * 201 creation.
 */
public record TransferResult(
        Transfer transfer,
        boolean isReplay
) {
}
