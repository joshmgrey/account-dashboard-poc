package com.example.dashboard;

/**
 * Generic error payload returned for non-2xx responses, carrying a single
 * human-readable message. Used by global exception handling.
 */
public record ErrorResponse(
        String message
) {
}
