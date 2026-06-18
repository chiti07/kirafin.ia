# Kira Fintech Ledger — Runbook

## Prerequisites

- Java 21 (`sdk use java 21.xxx` or `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`)
- Docker + Colima running (`colima start` if not already)

---

## 1. Start the database

```bash
docker compose up -d
```

Postgres 16 starts at `localhost:5432`, DB `kiraledger`, user/pass `kira/kira`.  
Flyway migrations run automatically on app start.

---

## 2. Run the app

```bash
./gradlew bootRun
```

App starts on port **8080**. Default API key: `dev-key-change-me`.

---

## 3. Run the tests

```bash
./gradlew test
```

Expected: **8/8 tests pass** (3 fee unit tests + 5 ledger integration tests).

To run a single test class:
```bash
./gradlew test --tests "com.kirafintech.ledger.LedgerIntegrationTest"
./gradlew test --tests "com.kirafintech.ledger.FeeCalculatorTest"
```

---

## 4. API walkthrough — Northwind end-to-end flow

All requests require `X-API-Key: dev-key-change-me` except `/actuator/health`.

### 4.1 Health check (no auth)

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### 4.2 Check Northwind balance (starts at $0)

```bash
curl -H "X-API-Key: dev-key-change-me" \
  http://localhost:8080/api/v1/accounts/00000000-0000-0000-0000-000000000011/balance
# {"availableCents":0,"pendingCents":0,"currency":"USD"}
```

### 4.3 Simulate a USDC on-ramp (manual trigger)

Post a confirmed USDC deposit of 5,000 USDC (= 5,000,000,000 minor units).  
This triggers the off-ramp immediately (fee deduction + auto-ACH route).

```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "X-API-Key: dev-key-change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "northwind-test-001",
    "accountId": "00000000-0000-0000-0000-000000000011",
    "amountMinorUnits": 5000000000,
    "currency": "USDC",
    "type": "CRYPTO",
    "confirmed": true,
    "chain": "solana"
  }'
```

**What happens internally:**
1. `postInboundCredit` — credits Northwind's account $499,900 (after 1 BPS + $0.50 fees)
2. `RouteEngine` fires the standing route: `postOutboundDebit` of $4,200 (420,000 cents)
3. A `payout_job` is written atomically with the debit (outbox pattern)
4. `PayoutWorker` picks it up within 10s, calls `SimpleRailAdapter.initiatePayment`

### 4.4 Check balance after on-ramp + route fires

```bash
curl -H "X-API-Key: dev-key-change-me" \
  http://localhost:8080/api/v1/accounts/00000000-0000-0000-0000-000000000011/balance
# {"availableCents":79900,"pendingCents":79900,"currency":"USD"}
# $499,900 - $420,000 = $79,900
```

### 4.5 Idempotency — replay the same request

```bash
# Same idempotencyKey as 4.3 → returns the same transfer, no double-credit
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "X-API-Key: dev-key-change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "northwind-test-001",
    "accountId": "00000000-0000-0000-0000-000000000011",
    "amountMinorUnits": 5000000000,
    "currency": "USDC",
    "type": "CRYPTO",
    "confirmed": true,
    "chain": "solana"
  }'
# Returns HTTP 200 (not 201) with the original transfer ID — balance unchanged
```

### 4.6 Simulate pending crypto deposit (not yet spendable)

```bash
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "X-API-Key: dev-key-change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "northwind-pending-001",
    "accountId": "00000000-0000-0000-0000-000000000011",
    "amountMinorUnits": 1000000000,
    "currency": "USDC",
    "type": "CRYPTO",
    "confirmed": false,
    "chain": "solana"
  }'
# availableCents unchanged, pendingCents increases
```

---

## 5. Verify invariants directly in the database

```bash
docker compose exec postgres psql -U kira -d kiraledger
```

```sql
-- Double-entry invariant: must always be 0
SELECT SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) AS net
FROM entries;

-- All amount columns are BIGINT (no floats anywhere)
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name IN ('entries', 'transfers')
  AND column_name IN ('amount', 'amount_minor_units');

-- View payout jobs (outbox)
SELECT id, status, attempts, provider, created_at FROM payout_jobs ORDER BY created_at DESC;

-- View all entries for Northwind
SELECT e.direction, e.amount, e.currency, e.confirmed, e.created_at
FROM entries e
WHERE e.account_id = '00000000-0000-0000-0000-000000000011'
ORDER BY e.created_at;
```

---

## 6. Create a custom account

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "X-API-Key: dev-key-change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "clientName": "My Test Client",
    "omnibus": false
  }'
```

To create a sub-account under an existing parent:
```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "X-API-Key: dev-key-change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "clientName": "My Test Client - Sub",
    "omnibus": false,
    "parentAccountId": "00000000-0000-0000-0000-000000000010"
  }'
```

---

## 7. Seeded accounts reference

| UUID | Name | Role |
|------|------|------|
| `...000000000001` | Kira Platform Fees | Receives fee credits |
| `...000000000002` | Kira Liquidity Pool | Source for inbound fiat credits |
| `...000000000003` | Kira Crypto Suspense | Holds unconfirmed crypto |
| `...000000000010` | Northwind Coffee Co. | Omnibus parent |
| `...000000000011` | Northwind Coffee Co. - Main | Active sub-account with ACH route |

---

## 8. Fee arithmetic

For any USDC deposit:

| Input | Calculation |
|-------|------------|
| USDC minor units → USD cents | `usdcMinorUnits / 10_000` |
| Platform fee (1 BPS) | `(grossCents * 1) / 10_000` |
| Fixed fee | `50` cents ($0.50) |
| Net credited | `grossCents - platformFee - 50` |

Example: 5,000 USDC = 5,000,000,000 minor units → 500,000 gross cents → **499,900 cents net** ($4,999.00)

---

## 9. Deploy to Railway

```bash
# Install Railway CLI if needed
brew install railway

# Login and link project
railway login
railway link

# Set env vars (one-time)
railway variables set API_KEY=<32-char-hex> \
  SOLANA_WATCH_ADDRESS=<your-devnet-address> \
  SPRING_PROFILES_ACTIVE=staging

# Deploy
railway up
```

Verify: `curl https://<your-app>.railway.app/actuator/health`
