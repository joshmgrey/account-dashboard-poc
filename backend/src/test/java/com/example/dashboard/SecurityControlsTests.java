package com.example.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Covers the per-IP rate limiter and the attributes of the JWT auth cookie.
 *
 * <p>Budgets are set small and refill slowly (1 token/min) so a test can drain
 * a bucket deterministically. Each test uses its own client IP, so tests never
 * share a bucket. The cookie is forced to {@code Secure} here to verify the
 * production setting (the dev default is off for http://localhost).
 */
@SpringBootTest(properties = {
        "app.ratelimit.capacity=5",
        "app.ratelimit.refill-per-minute=1",
        "app.ratelimit.auth-capacity=3",
        "app.ratelimit.auth-refill-per-minute=1",
        "app.security.cookie.secure=true"
})
@AutoConfigureMockMvc
class SecurityControlsTests {

    private static final String VALID_LOGIN =
            "{\"username\":\"alice\",\"password\":\"Password123!\"}";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String badLogin(int i) {
        // A fresh username per attempt so account lockout (which also returns
        // 429) never triggers; only the rate limiter can.
        return "{\"username\":\"ratelimit-user-" + i + "\",\"password\":\"wrong\"}";
    }

    // ---------- Rate limiting ----------

    @Test
    void authEndpoint_returns429OnceIpBudgetIsExhausted() throws Exception {
        String ip = "10.0.0.1";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login").with(fromIp(ip))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badLogin(i)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login").with(fromIp(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badLogin(99)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.error").value("Too many requests"));
    }

    @Test
    void authEndpoint_blocksEvenValidCredentialsOnceThrottled() throws Exception {
        String ip = "10.0.0.2";
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login").with(fromIp(ip))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(badLogin(100 + i)));
        }

        // A brute-forcer who finally guesses right is still turned away.
        mockMvc.perform(post("/api/auth/login").with(fromIp(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void generalEndpoint_returns429OnceIpBudgetIsExhausted() throws Exception {
        String ip = "10.0.0.3";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/actuator/health").with(fromIp(ip)))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/actuator/health").with(fromIp(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
    }

    @Test
    void rateLimitAppliesBeforeAuthentication() throws Exception {
        // Unauthenticated calls still spend budget, so an attacker can't probe
        // protected endpoints for free.
        String ip = "10.0.0.4";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/accounts").with(fromIp(ip)))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(get("/api/accounts").with(fromIp(ip)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void budgetsAreTrackedPerIp() throws Exception {
        String throttledIp = "10.0.0.5";
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login").with(fromIp(throttledIp))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(badLogin(200 + i)));
        }
        mockMvc.perform(post("/api/auth/login").with(fromIp(throttledIp))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isTooManyRequests());

        // A different client is unaffected by the first one's exhausted bucket.
        mockMvc.perform(post("/api/auth/login").with(fromIp("10.0.0.6"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isOk());
    }

    // ---------- Auth cookie ----------

    @Test
    void loginCookie_isHttpOnlySecureAndSameSiteStrict() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(fromIp("10.0.1.1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ACCESS_TOKEN=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/")))
                // Expires with the JWT (30 minutes) rather than living forever.
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=1800")));
    }

    @Test
    void loginResponseBody_doesNotExposeTheToken() throws Exception {
        // The JWT lives only in the httpOnly cookie, never where JS can read it.
        mockMvc.perform(post("/api/auth/login").with(fromIp("10.0.1.2"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void logoutCookie_isClearedWithSameProtections() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(fromIp("10.0.1.3")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("ACCESS_TOKEN=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("eyJ"))));
    }
}
