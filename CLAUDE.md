# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A ledger and orchestration engine for **Kira Fintech** — a US-based virtual account infrastructure provider. The build is scoped to a single client, **Northwind Coffee Co.**, and must support the full Northwind flow end-to-end (see `DESIGN.md §3`).

The challenge runs 5 days. Days 1–2 (design) are complete. Days 3–4 are the build; Day 5 is a live presentation. Read `context/` for each day's brief and `context/glosary.md` for domain vocabulary.

## Non-negotiable invariants

These are hard rules enforced by the automated grader. Violating any is a critical failure:

1. **No negative balances** — enforce via `SELECT FOR UPDATE` on the account row, not application-level checks.
2. **No floating-point money** — all amounts are `BIGINT` integer minor units (USD = cents, USDC/USDT = 6 decimal units). A `float` anywhere on a balance path is an automatic deduction.
3. **Idempotent everything** — every operation that moves money has a precisely defined idempotency key (see `DESIGN.md §10`). A retried request or twice-delivered webhook must never move money twice.
4. **No credit before confirmation** — inbound crypto deposits are `confirmed = false` until the chain threshold is met. Only `confirmed = true` entries count toward available balance.

## Settled architecture (do not relitigate)

All decisions below are in `DECISIONS.md`. Do not introduce alternatives without a new ADR.

- **Ledger:** double-entry, append-only `entries` table. No `UPDATE`/`DELETE` ever — corrections are reversal entries.
- **Balance:** always derived (`SUM` over entries), never stored. A cached snapshot is acceptable for reads but never authoritative.
- **Pending vs. available:** `confirmed boolean` on each entry row. Available = `WHERE confirmed = true`. Fiat inbound writes `confirmed = true` immediately; crypto inbound writes `confirmed = false` until threshold.
- **Concurrency:** `SELECT FOR UPDATE` on the `accounts` row, held for the entire balance-check + entry-write transaction. No optimistic retry.
- **Crash recovery:** outbox pattern — payout job written to `payout_jobs` in the same transaction as the ledger debit. Background worker makes the provider call and marks the job complete only after a confirmed response.
- **Fiat provider abstraction:** `FiatRailProvider` interface with `initiatePayment`, `getPaymentStatus`, `cancelPayment`. Two mock adapters (`SimpleRail`, `VerboseRail`) with different wire shapes. The payout worker never touches provider-specific fields.
- **Webhook security:** three layers — HMAC signature verification → replay guard via `webhook_events.event_id` unique constraint → out-of-order delivery handled by `block_height`/`sequence` ordering.
- **Fees:** always posted as separate ledger entries in the same DB transaction as the transfer. Never folded into the transfer amount.
- **Account hierarchy:** single `accounts` table with `parent_account_id` (self-referencing FK) and `is_omnibus boolean`. Sub-client sum must equal omnibus available balance — checked at EOD reconciliation.

## Idempotency key formulas

| Operation | Key |
|---|---|
| Inbound crypto deposit | `chain:tx_hash:log_index` |
| Outbound fiat payout | `transfer_id` (UUID) |
| Outbound crypto send | `transfer_id` (UUID) |
| Route execution | `account_id:route_id:trigger_transfer_id` |
| Webhook delivery | `provider:event_id` |
| Off-ramp conversion | `inbound_transfer_id:offramp` |

## Key reference files

| File | Purpose |
|---|---|
| `DESIGN.md` | Full system design: domain model, data model, concurrency hazards, vendor abstraction, idempotency |
| `DECISIONS.md` | 11 ADRs — every architectural call and its rationale |
| `context/glosary.md` | Domain vocabulary (Account, Transfer, Ramp, Route, Fee, etc.) |
| `context/day3.md` | Day 3 build brief — current deliverable |

## Reconciliation

EOD reconciliation is a query over the append-only entries table, not a manual process. Two mismatch types to detect and surface:
- **Settled-with-no-entry** — rail/chain moved money, no ledger record (missed webhook or watcher gap).
- **Entry-never-confirmed** — ledger recorded a movement that never settled (auto-flag only; no auto-correct in scope).

## Open questions (unresolved assumptions)

Tracked in `DECISIONS.md` parking lot. When making implementation calls on these, add a new ADR rather than hardcoding silently:
- On-ramp FX rate source (hardcoded mock for Days 3–4)
- Route conditions beyond "on-inbound-credit, always fire"
- ACH partial fill handling (treat as failed + reverse debit for now)
- Confirmation thresholds per chain (Solana: 32 slots, Polygon: 128 blocks, Tron: 20 blocks — ADR-006 is still Draft)