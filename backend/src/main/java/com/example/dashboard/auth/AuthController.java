package com.example.dashboard.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.dashboard.security.AuthCookieService;
import com.example.dashboard.security.JwtService;
import com.example.dashboard.security.LoginAttemptService;
import com.example.dashboard.user.AppUser;
import com.example.dashboard.user.UserStore;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // A valid throwaway BCrypt hash used to keep failed-login timing roughly
    // constant whether or not the username exists, mitigating user enumeration.
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserStore userStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthCookieService authCookieService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(UserStore userStore,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          AuthCookieService authCookieService,
                          LoginAttemptService loginAttemptService) {
        this.userStore = userStore;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
        this.loginAttemptService = loginAttemptService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        String username = request.username();

        if (loginAttemptService.isLocked(username)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Account temporarily locked. Try again later.");
        }

        AppUser user = userStore.findByUsername(username).orElse(null);
        boolean passwordMatches = user != null
                ? passwordEncoder.matches(request.password(), user.passwordHash())
                // Still run a hash comparison so timing doesn't reveal whether
                // the username exists.
                : runDummyComparison(request.password());

        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(username);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid username or password");
        }

        loginAttemptService.recordSuccess(username);
        String token = jwtService.issueToken(user.username());
        ResponseCookie cookie = authCookieService.buildAccessTokenCookie(
                token, jwtService.getTtlSeconds());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(UserResponse.from(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = authCookieService.buildExpiredCookie();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        return userStore.findByUsername(authentication.getName())
                .map(user -> ResponseEntity.ok(UserResponse.from(user)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private boolean runDummyComparison(String rawPassword) {
        passwordEncoder.matches(rawPassword, DUMMY_HASH);
        return false;
    }
}
