package com.example.dashboard.user;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * In-memory user directory for the POC. Passwords are stored only as BCrypt
 * hashes. Replace with a real user repository when moving beyond the POC.
 */
@Component
public class UserStore {

    private final Map<String, AppUser> usersByUsername = new LinkedHashMap<>();

    public UserStore(PasswordEncoder passwordEncoder) {
        register(new AppUser("alice", "Alice Johnson",
                passwordEncoder.encode("Password123!")));
        register(new AppUser("bob", "Bob Martinez",
                passwordEncoder.encode("Password123!")));
    }

    private void register(AppUser user) {
        usersByUsername.put(user.username(), user);
    }

    public Optional<AppUser> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(usersByUsername.get(username));
    }
}
