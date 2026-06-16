package com.example.dashboard.account;

/**
 * Minimal account projection for directory/lookup use (e.g. resolving a
 * transfer destination). Deliberately excludes {@code balance} and
 * {@code status} so a user can confirm an account exists without learning
 * private financial details about an account they don't own.
 */
public record AccountDirectoryEntry(
        String id,
        String currency,
        String owner
) {
    public static AccountDirectoryEntry from(Account account) {
        return new AccountDirectoryEntry(
                account.id(),
                account.currency(),
                account.owner());
    }
}
