# Kira Fintech Ledger Engine

Double-entry ledger and off-ramp engine for **Northwind Coffee Co.** — built for the Kira Fintech 5-day challenge.

**Live URL:** https://kira-ledger-production.up.railway.app

---

## One-command local setup

```bash
docker compose up -d && ./gradlew bootRun
```

Requires: Java 21, Docker (Colima or Docker Desktop).

App starts on **:8080**. Default API key: `dev-key-change-me`.

---

## Run all tests

```bash
./gradlew test
```

Expected: **12 tests pass** — 3 fee unit tests + 5 ledger integration tests + 4 BDD scenarios.

```bash
# Run a specific suite
./gradlew test --tests "com.kirafintech.ledger.LedgerIntegrationTest"
./gradlew test --tests "com.kirafintech.ledger.bdd.LedgerCucumberRunner"
./gradlew test --tests "com.kirafintech.ledger.FeeCalculatorTest"
```

---

## Key endpoints

All endpoints require `X-API-Key: dev-key-change-me` except `/actuator/health`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/actuator/health` | Health check (no auth) |
| GET | `/api/v1/accounts/{id}/balance` | Available + pending balance |
| POST | `/api/v1/accounts` | Create account |
| POST | `/api/v1/transfers` | Manual inbound credit trigger |
| GET | `/api/v1/transfers/{id}` | Transfer detail |
| GET | `/api/v1/dashboard` | Live balances, fees, transfer state, recon summary |
| GET | `/api/v1/reconciliation/report` | On-demand reconciliation run |
| GET | `/api/v1/routes` | Active routes |

---

## Northwind end-to-end flow (curl walkthrough)

```bash
BASE=https://kira-ledger-production.up.railway.app
KEY=dev-key-change-me   # replace with real API_KEY on Railway

# 1. Health check
curl $BASE/actuator/health

# 2. Balance before (starts at $0)
curl -H "X-API-Key: $KEY" "$BASE/api/v1/accounts/00000000-0000-0000-0000-000000000011/balance"

# 3. Trigger USDC on-ramp (5,000 USDC = 5,000,000,000 minor units)
curl -X POST "$BASE/api/v1/transfers" \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "northwind-demo-001",
    "accountId": "00000000-0000-0000-0000-000000000011",
    "amountMinorUnits": 5000000000,
    "currency": "USDC",
    "type": "CRYPTO",
    "confirmed": true,
    "chain": "solana"
  }'
# Credits $4,999.00 (499,900 cents) after 1 BPS + $0.50 fees
# Route 1 fires: $4,200 ACH via SimpleRail
# Route 2 fires: 600 USDT on Polygon Amoy

# 4. Balance after routes fire
curl -H "X-API-Key: $KEY" "$BASE/api/v1/accounts/00000000-0000-0000-0000-000000000011/balance"
# {"availableCents":19900,"pendingCents":19900,"currency":"USD"}
# $499,900 - $420,000 (ACH) - $60,000 (USDT) = $19,900

# 5. Dashboard
curl -H "X-API-Key: $KEY" "$BASE/api/v1/dashboard"

# 6. Reconciliation report
curl -H "X-API-Key: $KEY" "$BASE/api/v1/reconciliation/report"

# 7. Idempotency — replay same request, no double-credit
curl -X POST "$BASE/api/v1/transfers" \
  -H "X-API-Key: $KEY" -H "Content-Type: application/json" \
  -d '{"idempotencyKey":"northwind-demo-001","accountId":"00000000-0000-0000-0000-000000000011","amountMinorUnits":5000000000,"currency":"USDC","type":"CRYPTO","confirmed":true,"chain":"solana"}'
# Returns HTTP 200 with original transfer ID — balance unchanged
```

---

## Seeded accounts

| UUID suffix | Name | Role |
|-------------|------|------|
| `...000001` | Kira Platform Fees | Receives fee credits |
| `...000002` | Kira Liquidity Pool | Source for inbound fiat credits |
| `...000003` | Kira Crypto Suspense | Holds unconfirmed crypto |
| `...000010` | Northwind Coffee Co. | Omnibus parent |
| `...000011` | Northwind Coffee Co. - Main | Active sub-account with 2 routes |

## Seeded routes (Northwind Main)

| Route | Amount | Provider | Destination |
|-------|--------|----------|-------------|
| Auto-ACH to Vendor | $4,200 (420,000 cents) | SimpleRail | routing=021000021;account=987654321 |
| Auto-USDT to Polygon Vendor | $600 → 600 USDT | Polygon (mock) | 0x742d35Cc... |

---

## Fee arithmetic

| Input | Formula |
|-------|---------|
| USDC minor units → USD cents | `amount / 10_000` |
| Platform fee (1 BPS) | `(grossCents × 1) / 10_000` |
| Fixed fee | `50 cents ($0.50)` |
| Net credited | `grossCents − platformFee − 50` |

Example: 5,000 USDC → 500,000 gross cents → **499,900 cents net** ($4,999.00)

---

## Architecture

- **Ledger**: double-entry, append-only `entries` table. Balance = `SUM` query, never stored.
- **No negative balances**: `SELECT FOR UPDATE` on account row + balance check in same transaction.
- **Idempotency**: unique constraint on `transfers.idempotency_key`; all money operations carry a key.
- **Crypto inbound**: Solana watcher polls devnet; writes `confirmed=false` until slot depth ≥ threshold, then confirms and triggers off-ramp.
- **Outbound routes**: `RouteEngine` fires all active routes per account on inbound credit. Payout written atomically with debit (outbox pattern). Worker polls `payout_jobs` with `FOR UPDATE SKIP LOCKED`.
- **Provider abstraction**: `FiatRailProvider` interface; `SimpleRailAdapter`, `VerboseRailAdapter`, `PolygonOutboundAdapter` — each with a different wire format.
- **Observability**: structured JSON logs (logstash-logback-encoder), `AlertingService` fires business alerts as log lines every 60s, `ReconciliationService` runs every 5 min.

See `DESIGN.md` and `DECISIONS.md` for full architecture decisions.
