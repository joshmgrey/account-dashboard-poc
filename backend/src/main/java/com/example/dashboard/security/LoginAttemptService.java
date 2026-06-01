package com.example.dashboard.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.dashboard.config.AppProperties;

/**
 * Tracks consecutive failed login attempts per key (username) and temporarily
 * locks the account after too many failures, blunting credential brute-force
 * and password-spraying attacks.
 */
@Component
public class LoginAttemptService {

    private record Attempts(int count, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptService(AppProperties properties) {
        this.maxAttempts = properties.getLogin().getMaxAttempts();
        this.lockDuration = Duration.ofMinutes(properties.getLogin().getLockMinutes());
    }

    public boolean isLocked(String key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts == null || attempts.lockedUntil() == null) {
            return false;
        }
        if (Instant.now().isAfter(attempts.lockedUntil())) {
            attemptsByKey.remove(key);
            return false;
        }
        return true;
    }

    public void recordFailure(String key) {
        attemptsByKey.compute(key, (ignored, current) -> {
            int count = (current == null ? 0 : current.count()) + 1;
            Instant lockedUntil = count >= maxAttempts
                    ? Instant.now().plus(lockDuration)
                    : null;
            return new Attempts(count, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attemptsByKey.remove(key);
    }
}
