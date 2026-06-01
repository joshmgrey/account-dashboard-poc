package com.example.dashboard.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.example.dashboard.config.AppProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Builds and reads the {@code httpOnly} cookie that carries the JWT. Keeping
 * the token in an {@code httpOnly} cookie means it is unreadable from
 * JavaScript, eliminating token theft via XSS. {@code SameSite=Strict}
 * prevents the cookie from being sent on cross-site requests, mitigating CSRF.
 */
@Service
public class AuthCookieService {

    private final AppProperties.Cookie cookieProps;

    public AuthCookieService(AppProperties properties) {
        this.cookieProps = properties.getSecurity().getCookie();
    }

    public ResponseCookie buildAccessTokenCookie(String token, long maxAgeSeconds) {
        return baseCookie(token)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    public ResponseCookie buildExpiredCookie() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieProps.getName(), value)
                .httpOnly(true)
                .secure(cookieProps.isSecure())
                .sameSite(cookieProps.getSameSite())
                .path("/");
    }

    public Optional<String> readToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (var cookie : request.getCookies()) {
            if (cookieProps.getName().equals(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    return Optional.of(value);
                }
            }
        }
        return Optional.empty();
    }
}
