# Account Dashboard POC

A proof-of-concept account dashboard: users authenticate, then see their own
accounts and balances. Built with a Spring Boot backend and a Vite + React +
TypeScript frontend, with security hardening against brute-force and
denial-of-service abuse.

## Project layout

```
account-dashboard-poc/
├── backend/    # Spring Boot REST API (Java 21, Maven, Spring Security)
└── frontend/   # Vite + React + TypeScript SPA
```

## Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 20+ and npm

## Demo credentials

Two in-memory users are seeded (passwords are BCrypt-hashed, never stored in
plaintext):

| Username | Password       |
| -------- | -------------- |
| `alice`  | `Password123!` |
| `bob`    | `Password123!` |

Each user only ever sees their own accounts.

## Running

Run the backend and frontend in two terminals:

```bash
# terminal 1 - backend (http://localhost:8080)
cd backend && mvn spring-boot:run

# terminal 2 - frontend (http://localhost:5173)
cd frontend && npm install && npm run dev
```

Then open `http://localhost:5173` and sign in.

## API

| Method | Path                 | Auth | Description                          |
| ------ | -------------------- | ---- | ------------------------------------ |
| POST   | `/api/auth/login`    | no   | Log in; sets the auth cookie         |
| POST   | `/api/auth/logout`   | no   | Clears the auth cookie               |
| GET    | `/api/auth/me`       | yes  | Current user (used to restore session) |
| GET    | `/api/accounts`      | yes  | The signed-in user's accounts        |
| GET    | `/api/accounts/{id}` | yes  | A single account owned by the user   |
| GET    | `/actuator/health`   | no   | Health check                         |

## How authentication works

1. `POST /api/auth/login` verifies the password (BCrypt) and issues a signed
   (HS256) JWT.
2. The JWT is returned in an **`httpOnly`, `SameSite=Strict`** cookie. Because
   it is `httpOnly`, JavaScript can't read it, so it can't be stolen via XSS.
   Because it is `SameSite=Strict`, the browser won't send it on cross-site
   requests, which mitigates CSRF.
3. Each request is authenticated statelessly from the JWT — there is no
   server-side session store.
4. The frontend never handles the raw token; it just calls the API with
   credentials and relies on the cookie.

## Security hardening

> **Note on AI-assisted development:** Much of this security baseline was
> scaffolded with Cursor as part of an AI-assisted workflow. I'm currently
> studying each pattern in depth to make sure I understand the rationale
> and tradeoffs, not just the code. See [`AI_WORKFLOW.md`](AI_WORKFLOW.md) for
> running notes on what was generated, what I'm verifying, and the patterns
> I'm working through.

This POC demonstrates several defensive measures:

- **Rate limiting (DoS defense):** a per-IP token-bucket filter
  (`RateLimitFilter` / `RateLimiter`) throttles requests. Auth endpoints get a
  much tighter budget than general traffic. The limiter's own memory is bounded
  so it can't be turned into an exhaustion vector. Over-limit requests get
  `429 Too Many Requests`.
- **Brute-force protection:** `LoginAttemptService` locks an account for a
  configurable window after too many consecutive failed logins.
- **User-enumeration resistance:** failed logins return a generic error, and a
  dummy BCrypt comparison runs for unknown users to keep response timing
  roughly constant.
- **Password storage:** BCrypt hashing via Spring Security's `PasswordEncoder`.
- **Security headers:** `Content-Security-Policy`, `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, and HSTS.
- **Input validation:** request bodies are validated and size-bounded.
- **Information-leak reduction:** error responses omit messages, stack traces,
  and the server header; actuator exposes only `health` without details.
- **Stateless sessions + least privilege:** all `/api/**` routes require
  authentication except login/logout.

### Configuration

Security settings live in `backend/src/main/resources/application.yml` under
`app.*`. Override these for any real deployment via environment variables:

| Env var                      | Purpose                                            |
| ---------------------------- | -------------------------------------------------- |
| `APP_SECURITY_JWT_SECRET`    | JWT signing secret (**must** be ≥ 32 bytes)        |
| `APP_SECURITY_COOKIE_SECURE` | Set `true` in production (requires HTTPS)          |

Rate-limit budgets and login lockout thresholds are also configurable there
(`app.ratelimit.*`, `app.login.*`).

> **Note:** This is a POC. Users and accounts are in-memory, and the default
> JWT secret and `Secure=false` cookie are dev-only. Before any real use,
> supply a strong secret, enable secure cookies behind HTTPS, and back users
> and accounts with a real datastore.

## Frontend production build

```bash
cd frontend
npm run build      # outputs static assets to frontend/dist
npm run preview    # serve the production build locally
```

The Vite dev server proxies `/api/*` to the backend on port 8080
(see `vite.config.ts`), so the browser only ever talks to one origin in
development.
