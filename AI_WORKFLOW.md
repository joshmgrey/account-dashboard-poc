# AI-assisted development

This project is being built with an AI-assisted workflow (Cursor). This file is
a living log of how the AI was used, what was generated versus written by hand,
and — most importantly — the patterns I'm studying to make sure I understand the
rationale and tradeoffs rather than just accepting the code.

> The goal isn't to hide that AI was involved; it's to be deliberate about it.
> Anything scaffolded by the AI gets reviewed, and the non-obvious parts get
> studied and documented here.

## How I'm using the AI

- **Scaffolding:** generating the initial project skeleton (Spring Boot +
  Vite/React) and boilerplate-heavy files (config classes, DTOs, filter
  wiring).
- **Security baseline:** drafting the first pass of auth, rate limiting, and
  hardening so there's a concrete, reviewable starting point.
- **Review prompts:** asking the AI to explain *why* a pattern is used and what
  the alternatives/tradeoffs are, then verifying those claims independently.

What I'm **not** doing: merging code I don't understand, or treating generated
security code as correct by default. Security-relevant code in particular gets
a closer read and is verified against primary sources (Spring Security docs,
OWASP).

## Verification checklist

Because the generation environment couldn't run builds, these still need to be
confirmed locally (see the README "Running" section):

- [x] `mvn test` passes (backend compiles, auth tests green)
- [x] `npm install && npm run build` passes (frontend typechecks/builds)
- [x] Manual smoke test: login, see own accounts, logout, 401 when logged out
- [x] Rate limit returns `429` under rapid repeated requests
- [x] Login lockout triggers after repeated failures

## Patterns I'm studying

Notes-to-self on the non-obvious decisions in this codebase. Updated as I work
through each one.

### Auth: JWT in an httpOnly cookie vs. bearer token in localStorage
- **What was chosen:** stateless HS256 JWT delivered in an `httpOnly`,
  `SameSite=Strict` cookie.
- **Why:** `httpOnly` keeps the token unreadable from JS, so an XSS bug can't
  exfiltrate it. `SameSite=Strict` means the browser won't attach the cookie to
  cross-site requests, which is what mitigates CSRF here.
- **Tradeoffs to understand:** cookies are sent automatically (the reason CSRF
  is even a concern); `SameSite=Strict` can break some cross-site navigation
  flows; stateless JWTs can't be revoked before expiry without extra
  machinery (a denylist or short TTL + refresh tokens).
- **Status:** studying token revocation / refresh-token rotation as a follow-up.

### CSRF protection disabled
- **What was chosen:** Spring Security CSRF is turned off.
- **Why it's claimed to be safe:** the auth cookie is `SameSite=Strict`, so it
  isn't sent on cross-site requests, removing the classic CSRF vector; the API
  is also stateless with no session cookie.
- **What I need to confirm:** that `SameSite=Strict` is sufficient for this
  app's flows, and whether I'd want defense-in-depth (e.g. a custom header
  check) anyway. This is the decision I most want to pressure-test.

### Rate limiting (token bucket) for DoS defense
- **What was chosen:** in-memory per-IP token-bucket filter, stricter budget on
  `/api/auth/*`, with the limiter's own maps bounded to avoid becoming a
  memory-exhaustion vector.
- **Tradeoffs to understand:** in-memory state doesn't work across multiple
  instances (would need Redis/a shared store); `getRemoteAddr()` is correct
  only when not behind a proxy (otherwise need a trusted forwarded header);
  per-IP limiting is coarse (NAT, shared IPs).

### Brute-force lockout + user-enumeration resistance
- **What was chosen:** lock an account after N failures; return a generic error
  and run a dummy BCrypt comparison for unknown users.
- **Why:** generic errors + constant-ish timing avoid leaking which usernames
  exist; lockout slows credential stuffing.
- **Tradeoffs to understand:** account lockout can itself be abused for
  denial-of-service against a specific user; alternatives like exponential
  backoff or CAPTCHA exist.

### BCrypt password hashing
- **What was chosen:** Spring Security's `BCryptPasswordEncoder`.
- **What I'm reviewing:** work factor (cost) selection and how it should scale
  with hardware; how this would migrate to argon2 if needed.

### Security headers (CSP, HSTS, frame options, etc.)
- **What was chosen:** `Content-Security-Policy`, `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, HSTS.
- **What I'm reviewing:** whether the CSP is tight enough for the real
  frontend (it currently assumes same-origin assets), and HSTS implications
  (only meaningful over HTTPS; preload list considerations).

## Learning log

### Day 1 — Rate limiting deep dive
Cursor scaffolded a token-bucket rate limiter with separate buckets for general
traffic and auth endpoints, plus LRU-style eviction at 50k keys. Spent an
afternoon tracing the code to understand the mechanism: the refill formula
(tokens accrue continuously at `refillPerMinute / 60_000` per millisecond), the
capacity cap (`Math.min(capacity, ...)` so the bucket never over-fills), the
`ConcurrentHashMap` + `computeIfAbsent` for thread-safe per-key buckets with
synchronized consume, and the eviction policy.

One limitation I noticed: eviction is an O(n) scan for the oldest-seen entry,
which is fine for a POC but a production system should use something like
Caffeine's O(1) LRU (and likely a shared store like Redis for multi-instance).

Biggest takeaway: the exercise of explaining each piece in plain English without
notes surfaced gaps that surface-level reading had glossed over.

## Open questions / follow-ups

- Token revocation and refresh-token rotation.
- Moving users/accounts to a real datastore (Postgres + JPA).
- Distributed rate limiting if this ever runs on more than one instance.
- Whether to add CSRF defense-in-depth despite `SameSite=Strict`.
