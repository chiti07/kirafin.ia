# Kira Fintech — Day 4 Completion Checklist

## Priority order
1. DECISIONS.md ADRs → 2. Tests passing → 3. Commit + push → 4. Railway deploy → 5. Grafana

---

## 1. DECISIONS.md — Missing ADRs
- [x] ADR-012: Solana watcher uses HTTP polling (not WebSocket)
- [x] ADR-013: In-memory ConfirmationTracker; DB is authoritative on restart
- [x] ADR-014: Two Railway services from same repo; profile-driven config
- [x] ADR-015: WebClient for Solana RPC — no solanaj/web3j SDK
- [x] ADR-016: Observability via structured logs; no external alerting infra
- [x] ADR-017: Business alert inventory and page-worthiness rationale

---

## 2. Tests — Verify all 12 pass
- [x] `FeeCalculatorTest` — 3/3 passed
- [x] `LedgerIntegrationTest` — 5/5 passed (incl. concurrency test)
- [x] `LedgerCucumberRunner` — 4/4 BDD scenarios passed

---

## 3. Commit + Push
- [x] Stage all Day 3 + Day 4 + Grafana changes
- [x] Commit with meaningful message
- [x] PR merged to `main` → Railway auto-deploy triggered

---

## 4. Railway Deploy
- [x] Deploy completes successfully on Railway
- [x] `GET /actuator/health` returns `{"status":"UP"}` on public URL
- [x] End-to-end curl walkthrough passes on public URL:
  - [x] POST `/api/v1/transfers` with USDC deposit → 499,900 cents credited
  - [x] Routes fire → 2 payout jobs created (ACH + Polygon)
  - [x] Final balance = 19,900 cents ($199.00) ✓
  - [ ] Idempotency replay → same transfer ID, balance unchanged
  - [ ] `GET /api/v1/dashboard` returns live data
  - [ ] `GET /api/v1/reconciliation/report` returns clean report

---

## 5. Grafana (local)
- [ ] `/actuator/prometheus` accessible without auth (no 401)
- [ ] Custom `kira_*` metrics appear in Prometheus at `http://localhost:9090`
- [ ] Grafana dashboard loads at `http://localhost:3000` (admin/admin)
- [ ] All 10 panels show data (no "No data" panels after a test transfer)

---

## Day 4 Definition of Done (from brief)
- [ ] Full Northwind flow runs live on public URL; guardrails verifiable
- [ ] Genuine concurrency test, BDD scenarios, and working recon job (both mismatch types)
- [ ] Structured logging with documented alert inventory (implemented)
- [ ] README has one-command setup, test instructions, and live URL
