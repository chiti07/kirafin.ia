# DESIGN.md — Kira Fintech Ledger & Orchestration Engine

> **Status:** Complete — Day 2 (Plan & Understanding)
> **Author:** Juan Luis Chitiva
> **Last updated:** 2026-06-16

---

## Table of Contents

1. [Business Problem](#1-business-problem)
2. [Domain Model](#2-domain-model)
3. [The Northwind Flow](#3-the-northwind-flow)
4. [System Architecture — C4 Diagrams](#4-system-architecture--c4-diagrams)
    - [L1 — System Context](#l1--system-context)
    - [L2 — Container Diagram](#l2--container-diagram)
    - [L3 — Component Diagram (Ledger Engine)](#l3--component-diagram-ledger-engine)
5. [Ledger Model](#5-ledger-model)
6. [Concurrency & Consistency Hazards](#6-concurrency--consistency-hazards)
7. [Data Model Sketch](#7-data-model-sketch)
8. [Open Questions](#8-open-questions)
9. [Vendor Abstraction](#9-vendor-abstraction)
10. [Idempotency Keys](#10-idempotency-keys)
11. [Trade-offs & Out-of-Scope](#11-trade-offs--out-of-scope)

---

## 1. Business Problem

Kira Fintech moves money across rails that are fundamentally different:

**The hard part:** keeping a single, correct set of books while money is simultaneously in-flight on all of these. A deposit that arrives on Solana isn't real money until it's confirmed. An ACH that's been sent can come back. A payout that was requested may or may not have hit the provider before a crash.

The ledger must be **the source of truth at every moment** — not the provider's dashboard.

---

## 2. Domain Model

### Account
A USD-denominated container belonging to a **Client** (Northwind Coffee Co.) or one of their **Sub-Clients**. It has a balance, but that balance is **never stored directly** — it is always derived by summing ledger entries. An account can receive inbound instructions (deposits expected) and holds routing rules.

### Omnibus Account
A Client's **top-level aggregation account**. Funds from all Sub-Clients are pooled here at the bank level, but the ledger keeps each Sub-Client's balance distinct via entries tagged to their own account IDs. The hierarchy is:

```
Client (e.g. Northwind Coffee Co.)
└── Omnibus Account          ← bank-level pool
    ├── Sub-Client Account A ← tracked separately on the ledger
    ├── Sub-Client Account B
    └── Sub-Client Account C
```

This means the sum of all Sub-Client available balances must always equal the Omnibus Account's available balance — a reconciliation invariant enforced at EOD.

### Transfer
Any movement of value touching an account. Three directions:

- `inbound` — money arriving
- `outbound` — money leaving
- `internal` — money moving between two accounts inside Kira

Two types: `fiat` (USD) and `crypto` (stablecoin).

### Ramp
The bridge between crypto and fiat:

- **Off-ramp:** stablecoin arrives on-chain → fees applied → USD credited to account
- **On-ramp:** USD debited from account → fees applied → stablecoin sent on-chain

A ramp is not just a conversion rate — it is an **atomic ledger operation** that must either complete fully or not at all.

### Route
A standing rule attached to an account: *"when condition X is met, automatically execute transfer Y."* Routes are evaluated after every inbound credit. They enable the auto-payout behavior in the Northwind flow.

### Fee
Every fee is a **ledger entry**, not a column. Platform fee (% of volume) + fixed pass-through + optional client markup are all posted as separate `internal` transfers between the client account and Kira's fee account. Fees are itemized so reconciliation is always exact.

---

## 3. The Northwind Flow

The canonical end-to-end sequence this system must support:

```
Counterparty
    │
    │  sends 5,000 USDC on Solana
    ▼
[Blockchain Watcher]
    │  detects tx, waits for confirmations
    ▼
[Off-Ramp Engine]
    │  converts USDC → USD
    │  posts fee entries (platform + pass-through)
    │  credits ~$4,995 USD to Northwind account  ← ledger write
    ▼
[Route Engine]
    │  evaluates standing rules for Northwind
    ├──▶ Route 1: ACH $4,200 → Vendor account
    │       posts debit + ACH provider call (idempotent)
    └──▶ Route 2: 600 USDT on Polygon → destination wallet
             posts debit + on-ramp + blockchain send (idempotent)
    ▼
[EOD Reconciliation]
    │  derives all balances from entries
    │  cross-checks with provider statements
    └──▶ reconciliation report
```

**Key invariants at each step:**
- The blockchain deposit is not credited until `N` confirmations (reorg-safe threshold)
- Fee entries are posted atomically with the credit — you can't credit without also posting fees
- Each route execution carries an idempotency key — retrying a route never moves money twice
- The ACH and crypto sends happen **after** the ledger debit — if the provider call fails, the debit is still there and a background job retries using the same idempotency key

### Sequence Diagram

![L1 Sequence Diagram](assets/images/diagrams/flow.png)

---

## 4. System Architecture — C4 Diagrams

### L1 — System Context

![L1 System Context Diagram](assets/images/c4/context-diagram.png)

---

### L2 — Container Diagram

![L1 System Context Diagram](assets/images/c4/container-diagram.png)


---

### L3 — Component Diagram (Ledger Engine)

![L1 System Context Diagram](assets/images/c4/component-diagram.png)

---

## 5. Ledger Model

### Core principle: double-entry, append-only

Every movement of value creates **two entries** — a debit from one account and a credit to another. The entries table is **never updated or deleted**. Balance is always `SUM(amount) WHERE account_id = X`.

### Entry anatomy

```
entry
├── id              UUID, primary key
├── transfer_id     FK → transfers (groups the double-entry pair)
├── account_id      FK → accounts
├── direction       ENUM('debit', 'credit')
├── amount          BIGINT (integer minor units — cents for USD, 6-decimal units for crypto)
├── currency        ENUM('USD', 'USDC', 'USDT', ...)
├── created_at      TIMESTAMPTZ (immutable)
└── metadata        JSONB (rail-specific details, chain tx hash, etc.)
```

> All rows in the same transaction. Atomic or nothing.

### Balance derivation

```sql
SELECT
  SUM(CASE WHEN direction = 'credit' THEN amount ELSE -amount END) AS balance
FROM entries
WHERE account_id = $1
  AND currency = 'USD';
```

No `balance` column anywhere. Snapshot/materialized balance can be cached for performance but the query above is always authoritative.

### Pending vs. Available balance

The glossary mandates keeping these distinct — **payouts may only draw on available balance**.

The distinction lives in the `ENTRIES` table via a `confirmed` boolean on each entry:

```sql
-- Available balance (confirmed entries only — safe to spend)
SELECT
  SUM(CASE WHEN direction = 'credit' THEN amount ELSE -amount END) AS available_balance
FROM entries
WHERE account_id = $1
  AND currency = 'USD'
  AND confirmed = true;

-- Pending balance (all entries including unconfirmed deposits)
SELECT
  SUM(CASE WHEN direction = 'credit' THEN amount ELSE -amount END) AS pending_balance
FROM entries
WHERE account_id = $1
  AND currency = 'USD';
```

An inbound crypto deposit is written immediately as `confirmed = false` (visible, pending). The Blockchain Watcher upgrades it to `confirmed = true` once the confirmation threshold is met. Fiat inbound entries are written as `confirmed = true` immediately (the rail guarantees are handled by the provider).

---

## 6. Concurrency & Consistency Hazards

### Hazard 1 — Concurrent outbound payouts (negative balance race)

**Scenario:** Two route firings both read a balance of $4,995, both see it's sufficient, both proceed to debit $4,200. Result: balance goes to -$3,405.

**Guardrail:** Row-level lock on the account row (`SELECT FOR UPDATE`) held for the duration of the balance check + entry write within a single DB transaction. No optimistic retry — the lock is cheap and the window is small.

### Hazard 2 — Duplicate webhook / retry (double credit)

**Scenario:** The blockchain watcher fires a credit event; the server crashes after the ledger write but before ACKing. The watcher retries and fires again.

**Guardrail:** Every inbound transfer is keyed on `(chain, tx_hash, log_index)`. The idempotency guard checks this key before any write and returns the original result on a duplicate — no second ledger entry is ever created.

### Hazard 3 — Crash between ledger debit and provider call

**Scenario:** Route fires, ledger debit is posted, server crashes before the ACH call reaches the provider. On restart, the debit exists but no payout happened. Or the call was made but the response was lost — we don't know if money moved.

**The crash window** is the gap between the ledger write and the confirmed provider response. Any process death inside this window must be recoverable to an exactly-once outcome.

**Guardrail:** Outbox pattern. The payout job is written to the DB in the same transaction as the ledger debit. A background worker reads the outbox and makes the provider call, marking the job complete only after a confirmed provider response. Idempotency key on the provider call prevents double-send on retry.

### Hazard 4 — Unconfirmed deposit credited too early (chain reorg)

**Scenario:** A Solana transaction is detected with 1 confirmation; we credit the account; the block is reorganized and the transaction disappears.

**Guardrail:** Blockchain watcher only triggers a credit after `N` confirmations (configurable per chain — e.g. 32 for Solana). Deposit stays in a `pending` state until threshold is met. The entry is written immediately as `confirmed = false`; the watcher flips it to `confirmed = true` — no money is ever duplicated, only the confirmation flag changes.

### Hazard 5 — Malicious or replayed webhooks

**Scenario:** A provider sends an inbound webhook claiming a deposit arrived. An attacker replays the same webhook, or sends a forged one. Either way, the system credits an account it shouldn't.

**Guardrail:** Three layers:
1. **Signature verification** — every inbound webhook must carry a provider signature (HMAC or similar); requests that fail verification are rejected before any processing.
2. **Replay protection** — webhook events carry a unique event ID; the idempotency guard rejects any event ID already seen.
3. **Out-of-order delivery** — events are processed in the order of their `sequence` or `block_height`, not arrival time. A late-arriving event for a transfer already finalized is a no-op.

### Reconciliation mismatch types

Two failure modes the EOD reconciler must detect:

| Type | Description | Action |
|---|---|---|
| **Settled-with-no-entry** | The rail/chain moved money but the ledger has no record | Alert + manual review; likely a missed webhook or watcher gap |
| **Entry-never-confirmed** | The ledger recorded a movement that never actually settled | Alert + reversal entry if settlement window has expired |

---

## 7. Data Model Sketch

> First-pass ERD — to be refined during implementation.

![Data model](assets/images/diagrams/data-model.png)

---

## 8. Open Questions

> Tracked here; answered in `DECISIONS.md` as calls are made.

- [ ] What confirmation threshold per chain? (Solana: 32? Polygon: 128?)
- [ ] How do we handle partial fills on ACH (provider sends less than requested)?
- [ ] Should routes support conditions beyond simple "on-inbound-credit"? (e.g. scheduled, threshold-based)
- [ ] On-ramp rate source — hardcoded mock rate for now or integrate a price feed?
- [ ] Reconciliation mismatch resolution — auto-flag only, or auto-correct?

---

## 9. Vendor Abstraction

### The problem

The brief requires two fiat rail providers with **deliberately different shapes** — different request formats, error codes, and idempotency mechanisms. The payout worker must not care which provider it's talking to.

### Interface definition

```
FiatRailProvider
├── initiatePayment(params: PaymentParams): Promise<PaymentResult>
├── getPaymentStatus(providerRef: string): Promise<PaymentStatus>
└── cancelPayment(providerRef: string): Promise<void>

PaymentParams
├── idempotencyKey   string   — caller-assigned, provider must honour
├── amount           bigint   — integer cents
├── currency         string   — always "USD" for fiat
├── destinationRef   string   — routing+account or equivalent
└── metadata         object   — rail-specific extras (memo, reference, etc.)

PaymentResult
├── providerRef      string   — provider's own transaction ID
├── status           ENUM('pending', 'processing', 'settled', 'failed')
└── raw              object   — original provider response (for audit)
```

### Two mock providers — different shapes, same interface

| | Provider A ("SimpleRail") | Provider B ("VerboseRail") |
|---|---|---|
| Auth | API key header | OAuth2 bearer token |
| Idempotency | `X-Idempotency-Key` header | `idempotency_token` body field |
| Amount format | Integer cents | Decimal string `"42.00"` |
| Error format | `{ error: string }` | `{ code: number, message: string, details: [] }` |
| Status poll | `GET /payments/{id}` | `GET /transactions?ref={id}` |

Each provider is a concrete class implementing `FiatRailProvider`. The adapter translates between the wire format and the common interface. The payout worker only ever calls the interface — it never touches provider-specific fields.

### Adding a 3rd provider

Create a new adapter class implementing `FiatRailProvider`. Register it in the provider registry with a config key. No changes to the worker, ledger, or routing logic.

---

## 10. Idempotency Keys

Every operation that moves money has a precisely defined idempotency key. The same key presented twice always returns the same outcome and never moves money twice.

| Scenario | Key formula | Enforced at |
|---|---|---|
| Inbound crypto deposit | `chain:tx_hash:log_index` | `webhook_events.event_id` + `transfers.idempotency_key` |
| Outbound fiat payout | `transfer_id` (UUID) | `payout_jobs.idempotency_key` → provider header/field |
| Outbound crypto send | `transfer_id` (UUID) | `payout_jobs.idempotency_key` → on-chain memo / nonce tracking |
| Route execution | `account_id:route_id:trigger_transfer_id` | `transfers.idempotency_key` before route fires |
| Webhook delivery | `provider:event_id` | `webhook_events.event_id` unique constraint |
| Off-ramp conversion | `inbound_transfer_id:offramp` | `transfers.idempotency_key` |

### How the guard works

1. Before any write, the system looks up `transfers.idempotency_key` (or `webhook_events.event_id`).
2. If found → return the original result. No second write.
3. If not found → proceed, write atomically with the key.

The lookup + write happens inside the same DB transaction as the ledger entries. There is no window between "key not found" and "key written" where a race could sneak in — the unique constraint on `idempotency_key` catches any concurrent duplicate and raises a conflict, which the caller handles by re-fetching the original result.

---

## 11. Trade-offs & Out-of-Scope

### Calls made and why

| Decision | What we chose | Why |
|---|---|---|
| Concurrency control | `SELECT FOR UPDATE` (pessimistic) | Correctness over throughput; same-account concurrent debits are rare |
| Balance storage | Derived from entries, never stored | Single source of truth; no divergence possible |
| Payout delivery | Outbox + background worker | Crash-safe; exactly-once delivery without distributed transactions |
| Crypto finality | Configurable confirmation threshold | Reorg risk is chain-specific; hardcoding is dangerous |
| Fee model | Fees as ledger entries | Full audit trail; reconciliation is always exact |
| Pending/available | `confirmed` flag on entries | Simplest model; no separate pending table; upgrade is a single row update |
| Vendor interface | Adapter pattern | Zero coupling between worker and provider; 3rd provider = new adapter only |

### Explicitly out of scope for Days 1–2

- **Real fiat rails** — both providers are mocked. Interface is designed to swap in real ones.
- **FX rate feed** — off-ramp uses a hardcoded mock rate. Real price feed is a provider adapter change.
- **Multi-currency accounts** — accounts are USD-denominated. USDC/USDT are transient (converted at ramp).
- **Complex route conditions** — routes fire on every inbound credit. Threshold-based or scheduled triggers are not in scope.
- **Auto-correction on reconciliation mismatch** — the reconciler flags discrepancies; human review resolves them.
- **ACH partial fills** — treated as a failed transfer; the ledger debit is reversed. Full-amount-only assumption.
- **Sub-client minimum balance** — no minimum balance rule exists in the brief. Zero is a valid terminal state (ADR-011).
- **Monitoring** — no monitoring for prod service implemented. Grafana service  is not polling metrics from Prometheus. 