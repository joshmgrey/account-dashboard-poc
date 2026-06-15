package com.example.dashboard.transfer;

import java.math.BigDecimal;

/**
 * Client payload for initiating a money transfer, carrying the destination
 * account and the amount to move.
 */
public record TransferRequest(
        String destination,
        BigDecimal amount
) {
}
