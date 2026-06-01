package com.example.dashboard.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. Size bounds cap the amount of work an attacker can force
 * (e.g. very long passwords being hashed) and reject obviously malformed input.
 */
public record LoginRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 128) String password
) {
}
