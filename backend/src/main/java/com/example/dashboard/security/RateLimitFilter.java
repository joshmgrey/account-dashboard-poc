package com.example.dashboard.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * First line of denial-of-service defense: rejects requests from a client once
 * its per-IP token bucket is empty. Authentication endpoints get a tighter
 * budget than general traffic.
 *
 * <p>The client key is the socket remote address ({@code getRemoteAddr}) rather
 * than a forwarded header, so it cannot be spoofed by clients. Behind a trusted
 * reverse proxy you would instead resolve the real IP from a forwarded header
 * configured via {@code server.forward-headers-strategy}.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String clientKey = clientKey(request);
        boolean allowed = isAuthEndpoint(request)
                ? rateLimiter.allowAuthRequest(clientKey)
                : rateLimiter.allowRequest(clientKey);

        if (!allowed) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAuthEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/auth/");
    }

    private String clientKey(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }
}
