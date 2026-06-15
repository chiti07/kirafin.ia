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

## Parking Lot — Unresolved Assumptions

> Items that need a call but are deferred until more context is available.

- **On-ramp FX rate source:** Using a hardcoded mock rate for Days 3–4. Real integration deferred.
- **Multi-currency accounts:** Assuming USD-only accounts for now. USDC/USDT are transient (ramp converts them).
- **Route conditions:** Implementing "on-inbound-credit, always fire" for the Northwind demo. Complex conditions (threshold-based, scheduled) are out of scope for Days 3–4 unless the brief expands.
- **ACH partial fill handling:** Assuming full-amount ACH for now. If a provider returns partial, treat as a failed transfer and reverse the ledger debit.
- **Reconciliation mismatch resolution:** Auto-flag only. No auto-correct in scope for this build.