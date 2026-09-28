// Load test for POST /api/accounts/{id}/transfers.
//
// Fires concurrent transfers in both directions between the same accounts (the
// pattern that deadlocks without consistent lock ordering), then checks that
// every balance equals its starting value plus the net of the transfers that
// succeeded, to the cent.
//
// The backend's per-IP rate limit (120 req/min) would reject most of this
// traffic, so start it with the limit raised for the test run only:
//
//   cd backend
//   mvn spring-boot:run -Dspring-boot.run.arguments="--app.ratelimit.capacity=1000000 --app.ratelimit.refill-per-minute=1000000"
//
// Then, from the repo root:
//
//   node scripts/load-test-transfers.mjs [--requests 1000] [--concurrency 50] [--warmup 200]
//
// Requires Node 18+ (built-in fetch). Uses the seeded demo users.

import { randomUUID } from "node:crypto";

const BASE_URL = process.env.BASE_URL ?? "http://localhost:8080";

function arg(name, fallback) {
  const i = process.argv.indexOf(`--${name}`);
  return i > -1 ? Number(process.argv[i + 1]) : fallback;
}

const REQUESTS = arg("requests", 1000);
const CONCURRENCY = arg("concurrency", 50);
const WARMUP = arg("warmup", 200);

// Transfers route: [user, source, destination]. Alice's two checking accounts
// trade in both directions, and alice and bob send to each other.
const ROUTES = [
  ["alice", "ACC-1001", "ACC-1002"],
  ["alice", "ACC-1002", "ACC-1001"],
  ["alice", "ACC-1001", "ACC-2001"],
  ["bob", "ACC-2001", "ACC-1001"],
];

async function login(username) {
  const res = await fetch(`${BASE_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password: "Password123!" }),
  });
  if (res.status !== 200) {
    throw new Error(`Login failed for ${username}: HTTP ${res.status}`);
  }
  const cookie = res.headers.getSetCookie().find((c) => c.startsWith("ACCESS_TOKEN="));
  return cookie.split(";")[0];
}

// Balances in integer cents, so reconciliation is exact.
async function balances(cookies) {
  const result = {};
  for (const cookie of Object.values(cookies)) {
    const res = await fetch(`${BASE_URL}/api/accounts`, { headers: { Cookie: cookie } });
    for (const account of await res.json()) {
      result[account.id] = Math.round(Number(account.balance) * 100);
    }
  }
  return result;
}

function randomAmountCents() {
  return 100 + Math.floor(Math.random() * 900); // $1.00 to $9.99
}

async function sendTransfer(cookies, [user, source, destination]) {
  const amountCents = randomAmountCents();
  const started = performance.now();
  const res = await fetch(`${BASE_URL}/api/accounts/${source}/transfers`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Idempotency-Key": randomUUID(),
      Cookie: cookies[user],
    },
    body: JSON.stringify({ destination, amount: (amountCents / 100).toFixed(2) }),
  });
  await res.arrayBuffer();
  return { status: res.status, ms: performance.now() - started, source, destination, amountCents };
}

async function run(cookies, count) {
  const results = [];
  let next = 0;
  async function worker() {
    while (next < count) {
      const i = next++;
      results.push(await sendTransfer(cookies, ROUTES[i % ROUTES.length]));
    }
  }
  const started = performance.now();
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, count) }, worker));
  return { results, seconds: (performance.now() - started) / 1000 };
}

function percentile(sorted, p) {
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

const cookies = { alice: await login("alice"), bob: await login("bob") };
const before = await balances(cookies);

const warmup = WARMUP > 0 ? await run(cookies, WARMUP) : { results: [] };
const measured = await run(cookies, REQUESTS);

const after = await balances(cookies);

// Expected balances from every successful transfer, warm-up included.
const expected = { ...before };
for (const r of [...warmup.results, ...measured.results]) {
  if (r.status === 201) {
    expected[r.source] -= r.amountCents;
    expected[r.destination] += r.amountCents;
  }
}
const mismatches = Object.keys(before).filter((id) => expected[id] !== after[id]);

const failures = {};
for (const r of measured.results) {
  if (r.status !== 201) failures[r.status] = (failures[r.status] ?? 0) + 1;
}
const latencies = measured.results.map((r) => r.ms).sort((a, b) => a - b);
const succeeded = measured.results.filter((r) => r.status === 201).length;

console.log(`Requests:     ${REQUESTS} (concurrency ${CONCURRENCY}, after ${WARMUP} warm-up)`);
console.log(`Succeeded:    ${succeeded}`);
console.log(`Failed:       ${REQUESTS - succeeded}${Object.keys(failures).length ? " " + JSON.stringify(failures) : ""}`);
console.log(`Throughput:   ${(REQUESTS / measured.seconds).toFixed(0)} req/sec`);
console.log(`Latency:      p50 ${percentile(latencies, 50).toFixed(1)} ms, p95 ${percentile(latencies, 95).toFixed(1)} ms, p99 ${percentile(latencies, 99).toFixed(1)} ms`);
console.log(`Reconciled:   ${mismatches.length === 0 ? "yes, every balance exact to the cent" : "NO: " + mismatches.join(", ")}`);

process.exit(mismatches.length === 0 && succeeded === REQUESTS ? 0 : 1);
