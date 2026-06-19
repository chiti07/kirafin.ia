# DECISIONS.md — Architecture Decision Records

> Lightweight ADR log. Every assumption, call, or open question resolved goes here.
> Format: **Context → Decision → Consequences**.

---

## Index

| # | Decision | Status | Date |
|---|---|---|---|
| [ADR-001](#adr-001) | Integer minor units for all monetary values | Accepted | 2026-06-14 |
| [ADR-002](#adr-002) | Double-entry, append-only ledger | Accepted | 2026-06-14 |
| [ADR-003](#adr-003) | Balance derived from entries, never stored | Accepted | 2026-06-14 |
| [ADR-004](#adr-004) | Per-account SELECT FOR UPDATE for concurrency | Accepted | 2026-06-14 |
| [ADR-005](#adr-005) | Outbox pattern for payout jobs | Accepted | 2026-06-14 |
| [ADR-006](#adr-006) | Confirmation thresholds per chain | Draft | 2026-06-14 |
| [ADR-007](#adr-007) | Fiat rails abstracted behind a single interface | Accepted | 2026-06-14 |
| [ADR-008](#adr-008) | Pending vs. Available balance via `confirmed` flag on entries | Accepted | 2026-06-14 |
| [ADR-009](#adr-009) | Omnibus account modeled as self-referencing parent in accounts table | Accepted | 2026-06-14 |
| [ADR-010](#adr-010) | Webhook security: signature verification + replay protection + ordering | Accepted | 2026-06-14 |
| [ADR-011](#adr-011) | Sub-client balance may reach zero — no intraday buffer | Accepted | 2026-06-15 |
| [ADR-012](#adr-012) | Solana watcher uses HTTP polling, not WebSocket | Accepted | 2026-06-16 |
| [ADR-013](#adr-013) | In-memory ConfirmationTracker; DB is authoritative | Accepted | 2026-06-16 |
| [ADR-014](#adr-014) | Two Railway services from same repo; profile-driven config | Accepted | 2026-06-16 |
| [ADR-015](#adr-015) | WebClient for Solana RPC — no solanaj/web3j SDK | Accepted | 2026-06-16 |
| [ADR-016](#adr-016) | Observability via structured logs; no external alerting infra | Accepted | 2026-06-17 |
| [ADR-017](#adr-017) | Business alert inventory and page-worthiness rationale | Accepted | 2026-06-17 |

---

## ADR-001

### Integer minor units for all monetary values

**Status:** Accepted

**Context:**
Floating-point arithmetic is unsuitable for financial calculations. `0.1 + 0.2 !== 0.3` in IEEE 754. The brief explicitly calls this out as an automatic deduction. Additionally, different chains use different decimal precisions (USDC on Solana: 6 decimals; ETH: 18 decimals).

**Decision:**
All monetary amounts are stored and computed as **`BIGINT` integer minor units**:
- USD → cents (1 USD = 100)
- USDC / USDT → micro-units (1 USDC = 1,000,000)

Per-chain decimal metadata is stored alongside the amount so it can always be correctly displayed.

**Consequences:**
- No floating-point anywhere in the codebase — lint rule will enforce this.
- Display layer must divide by the correct decimal factor.
- Cross-currency operations (off-ramp) require an explicit conversion step with a defined rate — no implicit casting.

---

## ADR-002

### Double-entry, append-only ledger

**Status:** Accepted

**Context:**
A single-entry ledger (just debiting/crediting a balance column) is easy to corrupt, hard to audit, and provides no trace of how a balance came to be. The brief requires a double-entry model.

**Decision:**
Every value movement creates exactly two `entries` rows: one debit, one credit, always in the same DB transaction. The `entries` table has no `UPDATE` or `DELETE` operations — ever. Corrections are made via reversal entries.

**Consequences:**
- Full audit trail is the entries table itself — no separate log needed.
- Reconciliation is straightforward: sum entries by account and compare.
- Bugs create visible evidence (imbalanced entries) rather than silent corruption.
- Slightly more complex write path, but the invariant is enforceable at the DB level (`CHECK` constraints, triggers).

---

## ADR-003

### Balance derived from entries, never stored

**Status:** Accepted

**Context:**
Storing a denormalized `balance` column creates two sources of truth that can diverge. Any bug that updates the balance but not the entries (or vice versa) causes silent inconsistency.

**Decision:**
No `balance` column on the `accounts` table. Balance = `SUM(amount) WHERE account_id = X AND direction = 'credit') - SUM(amount WHERE direction = 'debit')`. A **materialized/cached** balance snapshot may be maintained for read performance, but it is always re-derivable and never authoritative.

**Consequences:**
- Single source of truth: the entries table.
- Balance queries require an aggregation — acceptable with a proper index on `(account_id, currency)`.
- Any cache invalidation bug results in a stale display, not a corrupted ledger.

---

## ADR-004

### Per-account SELECT FOR UPDATE for concurrency control

**Status:** Accepted

**Context:**
Two concurrent outbound payouts can both read a sufficient balance, both proceed to debit, and together drive the account negative. This is the classic check-then-act race.

**Decision:**
Before any debit posting, acquire a row-level lock on the `accounts` row via `SELECT FOR UPDATE`. Hold the lock for the duration of the balance check + entry write in a single transaction. This serializes concurrent debits per account.

**Consequences:**
- Eliminates the negative-balance race at the cost of throughput under high concurrency for the same account.
- Acceptable trade-off: high-volume same-account concurrent debits are not the expected use case; correctness wins.
- Alternative (optimistic locking with retry) was considered but adds complexity without meaningful throughput gain for this use case.

**Assumption:** PostgreSQL is the database — `SELECT FOR UPDATE` is standard behavior.

---

## ADR-005

### Outbox pattern for payout jobs

**Status:** Accepted

**Context:**
If the server crashes after the ledger debit is posted but before the provider call is made (or after the call but before the response is recorded), we have an inconsistent state: money is debited from the ledger but we don't know if it was sent. Retrying naively risks double-sending.

**Decision:**
The payout job is written to a `payout_jobs` table **in the same DB transaction** as the ledger debit. A separate background worker polls the `payout_jobs` table and makes the provider call. The provider call carries an idempotency key derived from the `transfer_id`. The job is only marked `completed` after a confirmed provider response.

**Consequences:**
- Crash-safe: on restart, the worker finds the unprocessed job and retries.
- Provider idempotency key prevents double-send on retry (requires provider support — for mocked fiat rails, we implement this ourselves).
- Adds a background worker component to the architecture.
- Eventual consistency between ledger debit and actual send — acceptable; the window is small and the state is visible.

---

## ADR-006

### Confirmation thresholds per chain

**Status:** Draft — needs validation

**Context:**
Crediting an account before a blockchain transaction is final exposes us to reorg risk. Different chains have different finality guarantees.

**Tentative Decision:**
- **Solana:** 32 confirmed slots (~13 seconds) — conservative but reorg risk drops to near-zero.
- **Polygon:** 128 confirmed blocks (~4 minutes) — standard safe finality window.
- **Tron:** 20 confirmed blocks (~60 seconds).

These are configurable per deployment, not hardcoded.

**Open:** Need to validate these thresholds against production incident data or Kira's existing policy. Will revisit on Day 2.

**Consequences:**
- Deposits appear as `pending` until threshold is met — users see this state.
- Longer threshold = safer but slower UX. Shorter = faster but riskier.

---

## ADR-007

### Fiat rails abstracted behind a single provider interface

**Status:** Accepted

**Context:**
The brief requires two fiat providers with different shapes behind one abstraction (mocked). Different providers have different request/response formats, error codes, and idempotency mechanisms.

**Decision:**
Define a single `FiatRailProvider` interface with methods: `initiatePayment(params)`, `getPaymentStatus(id)`, `cancelPayment(id)`. Two concrete mock implementations (`ProviderA`, `ProviderB`) each translate to/from this interface. The payout worker only knows the interface.

**Consequences:**
- Adding a real provider later requires only a new adapter — no changes to the core.
- Mock implementations can simulate different failure modes (timeout, rejection, partial fill) for testing.
- Interface must be designed generously enough to accommodate both providers' needs without leaking abstraction.

---

---

## ADR-008

### Pending vs. Available balance via `confirmed` flag on entries

**Status:** Accepted

**Context:**
The glossary explicitly requires keeping pending and available balances distinct, and mandates that payouts may only draw on available balance. A deposit is not spendable until confirmed on-chain. We need two independently queryable balance views.

**Decision:**
Add a `confirmed boolean` column to the `entries` table. Inbound crypto deposits are written as `confirmed = false` on detection. The Blockchain Watcher flips the flag to `confirmed = true` once the confirmation threshold is met — this single-row update is the only mutation ever allowed on the entries table. Fiat inbound entries are written as `confirmed = true` immediately. Available balance queries filter `WHERE confirmed = true`. Pending balance queries include all entries.

**Consequences:**
- Two clean SQL queries serve both balance types — no separate tables or state machines.
- The watcher's confirmation upgrade is idempotent (setting `confirmed = true` twice is a no-op).
- The `confirmed` flag is the only column ever updated in the entries table — preserves the append-only spirit while avoiding a separate `pending_entries` table.

---

## ADR-009

### Omnibus account modeled as self-referencing parent in accounts table

**Status:** Accepted

**Context:**
Kira's model has a Client → Omnibus Account → Sub-Client Accounts hierarchy. Funds pool at the omnibus level at the bank but must be tracked separately per Sub-Client on the ledger. The invariant is: sum of all Sub-Client available balances = Omnibus available balance.

**Decision:**
A single `accounts` table with a nullable `parent_account_id` FK (self-referencing) and an `is_omnibus` boolean. The Omnibus Account has `parent_account_id = NULL` and `is_omnibus = true`. Sub-Client Accounts point to their parent Omnibus via `parent_account_id`. The EOD reconciler checks the sub-sum invariant as part of its run.

**Consequences:**
- Simple hierarchy representation without a separate `omnibus_accounts` table.
- Queries for all accounts under a client can use a recursive CTE or a single join.
- The invariant check is a derived aggregate — if it fails, it points to a missing or duplicated entry, not a corrupted balance column.

---

## ADR-010

### Webhook security: signature verification + replay protection + ordering

**Status:** Accepted

**Context:**
The glossary explicitly calls webhooks "untrusted" and requires: verify signatures, reject replays, handle duplicates and out-of-order delivery. A forged or replayed webhook could credit an account fraudulently.

**Decision:**
Three-layer defense:
1. **Signature verification** — Each provider's webhook carries an HMAC signature over the payload + timestamp. Requests that fail signature verification are rejected with `400` before any business logic runs. Provider secrets are stored in environment variables, never in the DB.
2. **Replay protection** — A `webhook_events` table stores the provider-assigned `event_id`. Before processing, the system checks for an existing row with that `event_id`. Duplicates return `200` (to stop the provider retrying) but execute no logic.
3. **Out-of-order delivery** — Events for the same transfer are processed in `block_height` / `sequence` order. A late-arriving event for a transfer already in a terminal state (`confirmed`, `failed`) is a no-op.

**Consequences:**
- `webhook_events` table adds a write on every inbound webhook — acceptable overhead.
- Providers that don't support signatures (mocked fiat rails in this challenge) skip step 1 but still pass through steps 2 and 3.
- A webhook that arrives before its predecessor (out-of-order) is queued, not dropped.

---

## ADR-011

### Sub-client balance may reach zero — no intraday buffer

**Status:** Accepted

**Context:**
The question arose whether sub-client balances should be allowed to hit exactly zero intraday, or whether the system should hold a small buffer to absorb edge cases (e.g. a fee posting after a full sweep).

**Decision:**
Allow zero. No buffer is held. The balance invariant is simply `available_balance >= 0`.

**Reasoning:**
1. **Fees are atomic** — fee entries are posted in the same DB transaction as the transfer debit. The pre-debit check validates `available >= transfer_amount + fees`. There is no window where a transfer succeeds and a fee then surprises a zero balance.
2. **No rounding drift** — integer minor units eliminate the fractional errors a buffer would guard against.
3. **A buffer is an implicit minimum balance** — clients have not agreed to one. If a business rule requires a minimum, it must be an explicit, configurable `min_balance` field on the account, not a hidden system constant.
4. **Zero is a valid terminal state** — a sub-client performing a full sweep should reach zero cleanly.

**Consequences:**
- The pre-debit check must include fees in its sufficiency test: `available >= amount + sum(fees)` — not just `available >= amount`.
- If a configurable minimum balance is introduced later, it is an account-level field, not a system default.

---

## ADR-012

### Solana watcher uses HTTP polling, not WebSocket

**Status:** Accepted

**Context:**
Two options exist for watching on-chain activity: (1) subscribe to a WebSocket stream from the Solana RPC node, or (2) periodically poll `getSignaturesForAddress`. WebSocket gives lower latency but requires reconnect logic on disconnect. HTTP polling is stateless from the transport layer's perspective.

**Decision:**
Use HTTP polling with a configurable interval (`SOLANA_POLL_INTERVAL_MS`, default 5 000 ms). On each cycle, fetch signatures newer than the last seen cursor, detect new USDC transfers, and route them through the confirmation tracker.

**Reasoning:**
- **Crash recovery is free.** On restart the watcher re-polls from the last cursor (in-memory cursor resets, but the DB idempotency key prevents double-credit — the worst case is a few seconds of re-scanning, not a duplicate posting).
- **Reconnect complexity is eliminated.** WebSocket clients must handle disconnect/reconnect, backpressure, and partial-message assembly. The operational risk outweighs the ~5-second latency advantage.
- **5 s is well within UX expectations.** Solana slot time is ~400 ms; a 32-slot confirmation threshold takes ~13 s regardless of detection latency. The polling delay is not the bottleneck.

**Consequences:**
- Detection latency is bounded by `poll-interval-ms`, not block time.
- On restart, the watcher re-scans recent history — the DB idempotency constraint ensures no double-credit.
- If Solana RPC becomes unavailable, the watcher logs errors and retries on the next cycle; `@Scheduled` thread survives because the body is wrapped in `try/catch`.

---

## ADR-013

### In-memory ConfirmationTracker; DB is authoritative on restart

**Status:** Accepted

**Context:**
After detecting an unconfirmed deposit, the watcher must track it until enough slots pass to confirm it. Two options: (1) persist tracker state to the DB on every poll cycle, or (2) keep it in memory and rely on the DB entries table to reconstruct state on restart.

**Decision:**
Use a `ConcurrentHashMap` in-memory tracker keyed by signature. On restart, the in-memory tracker is empty, but the DB contains all unconfirmed entries (`confirmed = false`). The watcher re-detects those signatures on the next poll, re-adds them to the tracker, and resumes confirmation counting from the current slot. The cost is a possible delay of up to one poll interval before confirmation resumes.

**Reasoning:**
- **Simpler write path.** Persisting tracker state would require a separate table and transactional updates every 5 seconds per pending deposit — overhead for a state that is ephemeral by design.
- **DB entries table already has everything needed.** `confirmed = false` rows with their `created_at` and `chain` fields contain enough to reconstruct intent.
- **The delay on restart is acceptable.** Confirmation takes tens of seconds anyway; a single-cycle delay on restart does not materially affect the UX.

**Consequences:**
- Server restart may delay confirmation by up to `poll-interval-ms`.
- No extra DB table or write path for tracker state.
- DB remains the only authoritative state for financial correctness; in-memory is purely operational.

---

## ADR-014

### Two Railway services from same repo; `SPRING_PROFILES_ACTIVE` drives config

**Status:** Accepted

**Context:**
The Day 3 brief requires staging/prod separation. Options: (1) separate repositories, (2) separate branches, (3) same repo + same branch, distinguished by environment variables.

**Decision:**
Single GitHub repository, single `main` branch. Two Railway services (`ledger-staging`, `ledger-prod`) each pointed at the same repo. `SPRING_PROFILES_ACTIVE=staging` / `=prod` is the only difference between them. Staging uses a separate Railway Postgres add-on. Secrets (`API_KEY`, `DATABASE_URL`, chain config) are set per-service in the Railway dashboard — never committed.

**Reasoning:**
- Separate repos double the maintenance surface for no benefit at this scale.
- Branch-based separation creates merge complexity; a single production-ready branch is cleaner.
- Profile-driven config is the Spring Boot idiom; it composes naturally with `application-{profile}.yml` overrides.

**Consequences:**
- Any push to `main` triggers deploy to both services. A Railway service can be paused independently if staging should not auto-deploy.
- Secret rotation must be done per-service in Railway; there is no shared secret store at this scale.
- `docker-compose.yml` represents the local equivalent of both services on one machine.

---

## ADR-015

### WebClient for Solana RPC — no solanaj/web3j SDK

**Status:** Accepted

**Context:**
The `solanaj` library (and similar JVM Solana SDKs) provide typed RPC clients. However, they have inconsistent Java version support and introduce transitive dependency conflicts, particularly with Java 25 and Spring Boot 3.x.

**Decision:**
Use Spring `WebClient` to make raw JSON-RPC calls directly to the Solana HTTP endpoint. Responses are parsed from `Map<String, Object>` using Jackson. Only the three methods needed are implemented: `getSignaturesForAddress`, `getTransaction`, and `getSlot`.

**Reasoning:**
- The Solana JSON-RPC spec is stable and well-documented. Three methods cover the entire use case.
- Eliminates library Java-version compatibility risk — `WebClient` ships with Spring Boot.
- Parsing `Map<String, Object>` adds a few lines of boilerplate but is explicit and testable.
- No SDK means no license audit, no transitive vulnerabilities from Solana tooling.

**Consequences:**
- Adding more Solana operations later requires implementing additional RPC wrappers manually.
- No SDK-level abstractions (e.g. typed `PublicKey`, `Transaction` objects) — amounts and addresses are `String`/`long`.
- Error handling on malformed RPC responses must be coded explicitly.

---

## ADR-016

### Observability implemented as structured logs; no external alerting infrastructure

**Status:** Accepted

**Context:**
The Day 4 brief explicitly states: *"Structured logging is enough to demonstrate the thinking. We want to see what you'd watch and why."* Options ranged from a full Prometheus + PagerDuty stack to pure log-based alerting.

**Decision:**
All observability signals are emitted as structured JSON log lines via `logstash-logback-encoder`. `AlertingService` evaluates six alert conditions every 60 seconds and emits `ERROR` or `WARN` log events with machine-parseable key=value fields. `ReconciliationService` runs every 5 minutes and logs both mismatch types. Prometheus metrics are exported via Micrometer for the Grafana dashboard, but the business alerts themselves live in the logs.

**Reasoning:**
- The evaluation criterion is *judgment about what to alert on and why* — not the alerting pipeline itself.
- Structured JSON logs can be ingested by any log aggregator (Datadog, CloudWatch, ELK) without code changes.
- A Prometheus/PagerDuty integration would add infrastructure complexity that obscures the signal; the log lines are the deliverable.

**Consequences:**
- In production, a log forwarder (Datadog Agent, Fluentd) would tail the structured logs and route `alert_*` events to an on-call channel.
- The 60-second evaluation window means worst-case alert delay is 60 seconds — acceptable for the use case.
- No deduplication of alerts: if a condition persists, the same log line fires every 60 seconds. A production system would add alert suppression (e.g. fire once, re-fire after 30 min).

---

## ADR-017

### Business alert inventory and page-worthiness rationale

**Status:** Accepted

**Context:**
Not every anomaly warrants waking an on-call engineer. The question is: *which signals indicate money is at risk or customers are blocked, right now?* The six alerts below were selected on this criterion.

**Decision:**

| Alert key | Level | Condition | Rationale |
|---|---|---|---|
| `alert_failed_settlement` | CRITICAL | Payout job exhausted retries in the last 5 min | Money debited from ledger, never reached the rail. Client shows reduced balance; vendor never received funds. Manual intervention required immediately. |
| `alert_balance_drift` | CRITICAL | `SUM(credits) − SUM(debits) ≠ 0` across all entries | Double-entry invariant broken. Any non-zero net is a data integrity failure — a missing or phantom entry. Stop new transactions until resolved. |
| `alert_reconciliation_break` (settled-with-no-entry) | CRITICAL | Payout job COMPLETED but transfer still PENDING | Rail moved money; our ledger has no record. Revenue leakage or ghost liability. |
| `alert_stuck_pending` | WARN | Transfer in PENDING status > 30 minutes | Payout worker may be down or blocked. Funds are debited but settlement is stalled; customers are waiting. |
| `alert_confirmation_backlog` | WARN | Crypto entry `confirmed=false` older than 15 minutes | Blockchain watcher may have lost connectivity. Deposit is visible on-chain but balance is not yet available to the customer. |
| `alert_db_connection_pressure` | WARN | Connection acquisition > 500 ms | Pool near exhaustion. Will degrade to timeout errors under continued load. Scale the pool or the DB before it becomes an outage. |

**Alerts deliberately excluded:**
- **High request rate** — not page-worthy on its own; monitor via dashboards.
- **Slow HTTP p99** — leading indicator, not an outage. Alert on error rate instead.
- **High JVM heap** — GC will handle it; alert on OOM errors, not heap %.
- **Reconciliation: entry-never-confirmed** — WARN, not CRITICAL. Funds are inaccessible but not lost; no money movement risk.

**Consequences:**
- Six is a small, focused inventory. Engineers can act on every alert without alert fatigue.
- Missing alert: provider-side webhook failure spikes (not implemented — no real provider in scope).
- Thresholds (30 min pending, 15 min unconfirmed, 500 ms DB) are configurable via environment variables for tuning without redeployment.

---

## Parking Lot — Unresolved Assumptions

> Items that need a call but are deferred until more context is available.

- **On-ramp FX rate source:** Using a hardcoded mock rate for Days 3–4. Real integration deferred.
- **Multi-currency accounts:** Assuming USD-only accounts for now. USDC/USDT are transient (ramp converts them).
- **Route conditions:** Implementing "on-inbound-credit, always fire" for the Northwind demo. Complex conditions (threshold-based, scheduled) are out of scope for Days 3–4 unless the brief expands.
- **ACH partial fill handling:** Assuming full-amount ACH for now. If a provider returns partial, treat as a failed transfer and reverse the ledger debit.
- **Reconciliation mismatch resolution:** Auto-flag only. No auto-correct in scope for this build.