package com.example.dashboard.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.dashboard.user.UserStore;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates each request from the JWT carried in the {@code httpOnly}
 * cookie. On a valid token the security context is populated; otherwise the
 * request continues unauthenticated and is rejected later by the authorization
 * rules. The filter never trusts the token's subject blindly: it confirms the
 * user still exists in the directory.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthCookieService authCookieService;
    private final UserStore userStore;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   AuthCookieService authCookieService,
                                   UserStore userStore) {
        this.jwtService = jwtService;
        this.authCookieService = authCookieService;
        this.userStore = userStore;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authCookieService.readToken(request)
                    .flatMap(jwtService::validateAndGetSubject)
                    .flatMap(userStore::findByUsername)
                    .ifPresent(user -> {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                user.username(),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER")));
                        authentication.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        filterChain.doFilter(request, response);
    }
}
