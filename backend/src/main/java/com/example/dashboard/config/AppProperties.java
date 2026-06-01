package com.example.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Strongly-typed binding for the {@code app.*} configuration tree.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Cors cors = new Cors();
    private final Security security = new Security();
    private final RateLimit ratelimit = new RateLimit();
    private final Login login = new Login();

    public Cors getCors() {
        return cors;
    }

    public Security getSecurity() {
        return security;
    }

    public RateLimit getRatelimit() {
        return ratelimit;
    }

    public Login getLogin() {
        return login;
    }

    public static class Cors {
        private String[] allowedOrigins = new String[0];

        public String[] getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String[] allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Security {
        @NestedConfigurationProperty
        private final Jwt jwt = new Jwt();
        @NestedConfigurationProperty
        private final Cookie cookie = new Cookie();

        public Jwt getJwt() {
            return jwt;
        }

        public Cookie getCookie() {
            return cookie;
        }
    }

    public static class Jwt {
        private String secret = "";
        private long expirationMinutes = 30;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }
    }

    public static class Cookie {
        private String name = "ACCESS_TOKEN";
        private boolean secure = true;
        private String sameSite = "Strict";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }
    }

    public static class RateLimit {
        private long capacity = 120;
        private long refillPerMinute = 120;
        private long authCapacity = 8;
        private long authRefillPerMinute = 8;

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillPerMinute() {
            return refillPerMinute;
        }

        public void setRefillPerMinute(long refillPerMinute) {
            this.refillPerMinute = refillPerMinute;
        }

        public long getAuthCapacity() {
            return authCapacity;
        }

        public void setAuthCapacity(long authCapacity) {
            this.authCapacity = authCapacity;
        }

        public long getAuthRefillPerMinute() {
            return authRefillPerMinute;
        }

        public void setAuthRefillPerMinute(long authRefillPerMinute) {
            this.authRefillPerMinute = authRefillPerMinute;
        }
    }

    public static class Login {
        private int maxAttempts = 5;
        private long lockMinutes = 15;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getLockMinutes() {
            return lockMinutes;
        }

        public void setLockMinutes(long lockMinutes) {
            this.lockMinutes = lockMinutes;
        }
    }
}
