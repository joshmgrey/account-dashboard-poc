# Account Dashboard POC

A proof-of-concept account dashboard with a Spring Boot backend and a Vite + React + TypeScript frontend.

## Project layout

```
account-dashboard-poc/
├── backend/    # Spring Boot REST API (Java 21, Maven)
└── frontend/   # Vite + React + TypeScript SPA
```

## Prerequisites

- Java 21+
- Maven 3.9+
- Node.js 20+ and npm

## Backend

REST API exposing sample account data.

```bash
cd backend
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

Endpoints:

| Method | Path                 | Description              |
| ------ | -------------------- | ------------------------ |
| GET    | `/api/accounts`      | List all accounts        |
| GET    | `/api/accounts/{id}` | Fetch a single account   |
| GET    | `/actuator/health`   | Service health check     |

The sample data lives in `AccountService`; swap it for a real persistence
layer when moving beyond the POC.

## Frontend

Single-page app that renders the account list.

```bash
cd frontend
npm install
npm run dev
```

The dev server runs on `http://localhost:5173` and proxies `/api/*` requests
to the backend on port 8080 (see `vite.config.ts`), so no CORS configuration is
needed in development. The backend also allows the Vite origin directly (see
`WebCorsConfig`) for cases where the SPA calls the API without the proxy.

### Production build

```bash
cd frontend
npm run build      # outputs static assets to frontend/dist
npm run preview    # serve the production build locally
```

## Typical dev workflow

Run the backend and frontend in two terminals:

```bash
# terminal 1
cd backend && mvn spring-boot:run

# terminal 2
cd frontend && npm install && npm run dev
```

Then open `http://localhost:5173`.
