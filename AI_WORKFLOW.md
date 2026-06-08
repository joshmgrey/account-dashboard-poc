Day 5 entry in AI_WORKFLOW.md — capture today's security headers work. Same structure as the others. Don't make it long; the topic is straightforward. The interesting things to capture:
Five headers, each defending a different attack class
The X-Frame-Options + frame-ancestors overlap as deliberate defense in depth across browser support
CSP as the actual XSS defense (the one you didn't have before HttpOnly + escaping)
The CSP tradeoff (tight policy breaks third-party integrations; each loosening is a trust surface expansion)# AI-assisted development

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

The generation environment couldn't run builds, so these were confirmed locally
afterward (see the README "Running" section). All passing as of Day 1:

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

### Day 2 — Cookie security, XSS, CSRF
Traced the login cookie flow: `AuthController.login()` issues a JWT, then
`AuthCookieService.buildAccessTokenCookie()` wraps it in a `ResponseCookie` with
`httpOnly` hardcoded `true`, `path='/'` hardcoded, and `secure`/`sameSite`
config-driven via `cookieProps`. Noticed that login and logout share the same
`baseCookie()` builder — important because browsers won't delete a cookie unless
the replacement's attributes (name, path, domain) match.

Studied XSS and CSRF attack mechanics and the specific defense each cookie flag
provides. Key nuance: `HttpOnly` stops cookie *exfiltration*, not the XSS attack
itself — the real XSS defense is preventing script execution in the first place
via output encoding, framework defaults, and CSP. `SameSite` and `Secure` are
about CSRF and transport, not XSS.

### Day 3 — Login lockout
Traced `LoginAttemptService` to understand how repeated failed logins are
handled. Per-username failure tracking lives in a
`ConcurrentHashMap<String, Attempts>`, where `Attempts` is an immutable record
bundling the failure count and an optional lock expiry. The record exists for
atomicity — bundling the two fields into one immutable value lets `compute()`
update both atomically per key. Two separate maps would race.

After 5 failures the account locks for 15 minutes. Both are configurable via
`AppProperties.Login`. Unlock is lazy — there's no background sweeper. The
expiry just gets checked on the next login attempt for that user, and the entry
is removed at that point.

Two limitations worth knowing.

First, the map isn't bounded. Unlike the rate limiter, there's no LRU eviction
or size cap. An attacker could spoof many usernames to grow the map and exhaust
memory. In practice the rate limiter (8 token/min cap on auth endpoints) makes
this expensive per-IP, but a distributed attacker could still do it. Production
would add LRU bounding here too.

Second, lockout can itself be weaponized as targeted DoS. An attacker who knows
a username can deliberately fail five logins and lock that user out for 15
minutes, repeatable indefinitely. The implementation uses straight
username-based hard lockout — the simplest version of the pattern. Real systems
usually combine multiple signals: username+IP keying, exponential backoff,
CAPTCHA after N failures, or an email-verify recovery path. Each has its own
tradeoff (e.g. IP-keying weakens the brute-force defense against IP-rotating
attackers). The README already flags this tradeoff in "Tradeoffs to understand."

Biggest takeaway tying today to the previous deep dives: rate limiting, cookies,
and lockout don't defend the same things — they defend overlapping attacks at
different layers. The rate limiter caps per-IP request volume. Lockout caps
per-username failure volume. Cookie flags control what the browser will give up
to script and where it'll attach credentials. Defense in depth: no one layer is
sufficient against a determined attacker, but together they make the most common
attacks too expensive to be worth it.

### Day 4 — JWT internals
Traced `JwtService` to understand what the token actually *is* and why the
stateless model works the way it does.

The signature mechanism. A JWT is three base64url segments —
`header.payload.signature`. The signature is an HMAC-SHA-256 of the literal
string `header.payload`, computed with the server's secret key
(`Keys.hmacShaKeyFor(secret)` → `signWith(signingKey)`). On the way back in,
`parseSignedClaims()` recomputes that HMAC over the received `header.payload`
and compares it to the supplied signature. Because the secret never leaves the
server, a client can read the payload but can't forge or tamper with it — any
edit changes the hash and fails verification. Worth remembering: the payload is
*signed, not encrypted*, so nothing secret should go in it (here it's just the
username as `subject`).

Stateless vs. sessions (the upside). Validity is derived purely from the
signature + the `expiration` claim, so authenticating a request is a local
crypto check — no per-request DB/session lookup. That also makes horizontal
scaling trivial: any instance can verify any token with just the shared secret,
so there's no sticky sessions or shared session store to stand up.

The revocation tradeoff (the downside). The flip side of "no server state" is
that there's nothing to delete to kill a token early. A signed, unexpired JWT
stays valid until its `expiration` no matter what — logout on the client just
drops the cookie, but the token itself would still verify if replayed. You
can't revoke before expiry without reintroducing server state (a denylist /
token-version check), which gives back the statelessness you bought.

Mitigation pattern: keep access tokens short-lived and pair them with a
longer-lived refresh token. A short TTL bounds the damage window of a leaked
token; the refresh token (revocable, stored server-side) is exchanged for new
access tokens, so you get most of the stateless benefit while keeping a
revocation lever.

This code's specific limitation: tokens are single-purpose access tokens with a
30-minute TTL (`app.security.jwt.expiration-minutes`) and **no refresh
mechanism**. So the practical behavior is: a token is irrevocable for up to 30
minutes, and when it expires the user is simply forced to log in again (no
silent refresh). Fine for a POC; a real deployment would add refresh-token
rotation (already on the follow-ups list).

Takeaway: the signature is the whole trust model — verifying it *is* the
"session." Everything else about JWTs (the scaling win, the revocation pain) is
a direct consequence of that one design choice.

### Day 5 — Security headers
Reviewed the response headers set in `SecurityConfig`. Straightforward topic,
but the point is that each one defends a *different* attack class:
- **`Content-Security-Policy`** — restricts what the page can load/execute
  (XSS, injection).
- **`X-Frame-Options: DENY`** — blocks framing (clickjacking).
- **`X-Content-Type-Options: nosniff`** — stops MIME sniffing (content-type
  confusion attacks).
- **`Referrer-Policy: no-referrer`** — prevents URL/data leakage via the
  `Referer` header.
- **HSTS** — forces HTTPS (transport downgrade / SSL stripping).

Two things worth noting. First, `X-Frame-Options: DENY` and the CSP
`frame-ancestors 'none'` directive overlap on purpose — they defend the same
clickjacking attack, but CSP `frame-ancestors` is the modern mechanism while
`X-Frame-Options` is the legacy header older browsers still rely on. Setting
both is deliberate defense in depth across browser support.

Second, CSP is the piece that finally gives a *real* XSS defense — the thing I
didn't have on Day 2 when the takeaway was "`HttpOnly` limits the blast radius
but doesn't stop the attack." Output encoding + React's escaping prevent most
injection; CSP backstops it by refusing to execute injected/inline script even
if something slips through. The current policy is tight (`default-src 'self'`,
`object-src 'none'`, `base-uri 'self'`).

The tradeoff: a tight CSP breaks third-party integrations (analytics, embedded
widgets, CDN assets, inline styles/scripts). Every loosening — adding a host to
a directive, allowing `'unsafe-inline'` — is an expansion of the trust surface,
so the policy is only as strong as its most permissive directive. For this app,
same-origin everything keeps it strict; that gets harder the moment real
third-party scripts show up.

## Open questions / follow-ups

- Token revocation and refresh-token rotation.
- Moving users/accounts to a real datastore (Postgres + JPA).
- Distributed rate limiting if this ever runs on more than one instance.
- Whether to add CSRF defense-in-depth despite `SameSite=Strict`.
