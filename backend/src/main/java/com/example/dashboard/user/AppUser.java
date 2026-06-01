package com.example.dashboard.user;

/**
 * An application user. {@code passwordHash} is a BCrypt hash; the plaintext
 * password is never stored.
 */
public record AppUser(
        String username,
        String displayName,
        String passwordHash
) {
}
