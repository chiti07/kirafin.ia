# Candidate · Glossary (send with Day 1)

> The shared vocabulary for the challenge. These are the terms the briefs assume you know. When something here is ambiguous, make a call and write it down in `DECISIONS.md` — that judgment is part of what we evaluate.
>

## Accounts & balances

- **Account** — A USD-denominated account belonging to a **Client** or a **Sub-Client**. Holds balances and the inbound instructions (where money can arrive). Money is always tracked in USD on the books, regardless of the rail it arrived on.
- **Client / Sub-Client** — A Client is a customer of Kira (e.g. Northwind Coffee Co.). A Sub-Client is an account *under* a Client — Kira's clients serve their own customers, so a Client can hold many Sub-Client accounts.
- **Omnibus account** — The Client's top-level account that aggregates funds; individual Sub-Client balances are tracked within it. Funds are pooled at the bank but kept distinct on your ledger.
- **Balance (derived)** — A balance is **never a stored, mutated number**. It is *derived* by summing the ledger entries for an account. The ledger is the source of truth; the balance is a query.
- **Pending vs. Available** — *Pending* funds are seen but not yet usable (e.g. a deposit awaiting on-chain confirmations). *Available* funds can be spent. A payout may only draw on **available** balance. Keeping these distinct is mandatory.

## Money movement

- **Transfer** — Money moving in or out of an account. Defined by a **direction** (`inbound` / `outbound` / `internal`) and a **type** (`fiat` or `crypto`).
- **Inbound / Outbound / Internal** — *Inbound*: money arriving (a deposit). *Outbound*: money leaving (a payout). *Internal*: a movement between two Kira accounts that never leaves the platform.
- **Fiat rail** — A traditional money-movement network: **ACH**, **Wire**, **SWIFT**, **FedNow**. Each settles at a different speed and has different failure modes. *In this challenge the fiat rails are mocked behind a provider abstraction.*
- **Crypto leg** — The on-chain side of a transfer, using **stablecoins** (**USDC**, **USDT**) on **Solana**, **Polygon**, or **Tron**. *In this challenge the crypto leg is real, on testnet (Solana devnet / Polygon Amoy).*
- **Stablecoin** — A token pegged to USD (USDC / USDT). Note: the same token has **different decimal precision per chain** — never assume.
- **Counterparty** — Whoever is on the other side of a transfer: the external sender of an inbound deposit, or the vendor receiving an outbound payout.

## Ramps & routing

- **Ramp** — Converting between fiat and crypto. **Off-ramp**: stablecoin in → USD credited to the account (apply fees). **On-ramp**: USD → stablecoin sent out.
- **Route** — A standing rule of the form *“when X arrives, automatically send Y.”* Routes are what make the engine an *orchestration* engine: an inbound deposit can automatically trigger one or more outbound payouts.
- **The Northwind flow** — The reference scenario you must support end to end: a counterparty sends **5,000 USDC on Solana** → detected on-chain → confirmations → **off-ramp** (fees applied, USD credited) → a **route fires** → pay a vendor **$4,200 via ACH** + send **600 USDT on Polygon** → validated, idempotent, reconciled at end of day.

## Fees

- **Fee** — Every fee is **itemized** as its own ledger entry — never folded silently into an amount. Three components:
    - **Platform fee** — a percentage by volume.
    - **Fixed pass-through** — a flat per-transaction cost passed through.
    - **Client markup** *(optional)* — an extra margin the Client adds on top.

## Ledger concepts

- **Double-entry** — Every movement is recorded as balanced entries (debits = credits). Money is never created or destroyed on the books — only moved between accounts.
- **Append-only** — Entries are written once and never updated or deleted. Corrections are *new* compensating entries. This gives you a full, auditable history.
- **Entry / Posting** — A single line in the ledger: an amount, an account, a direction, and what it relates to. Balances and fees are all just entries.
- **Reconciliation** — The end-of-day check that your ledger matches the outside world (a bank statement / on-chain truth). Because the ledger is append-only, reconciliation is a **query**, not a manual process.
- **The two mismatch types** — *Settled-with-no-entry*: the rail/chain moved money but your ledger has no record. *Entry-never-confirmed*: your ledger recorded a movement that never actually settled. A good recon job detects **both**.

## Web3 / on-chain

- **Confirmation / confirmation threshold** — The number of blocks that must build on top of a transaction before you treat it as final. A deposit is **not money until confirmed** — credit only *available* balance once the threshold is met.
- **dReorg (reorganization)** — When a chain discards recently-mined blocks and rebuilds. A transaction you saw can disappear — which is exactly why confirmations matter.
- **Per-chain decimals / minor units** — The number of decimal places a token uses, which differs per chain. Always store money as **integer minor units** (or high-precision decimals) — never a `float`.
- **Testnet** — A non-production blockchain for testing (Solana **devnet**, Polygon **Amoy**) where tokens have no real value. The challenge's crypto leg runs here.
- **tx hash** — The unique identifier of an on-chain transaction. A natural idempotency key for inbound crypto deposits.

## Reliability & integration

- **Idempotency** — Doing the same operation more than once has the same effect as doing it once. A retried request or a webhook delivered twice must **never move money twice**.
- **Idempotency key** — The identifier used to dedupe an operation (e.g. a `tx hash` inbound, an `Idempotency-Key` header outbound). Define yours precisely.
- **Crash window** — The dangerous gap between writing to your ledger and calling the external provider (or vice versa). If the process dies in between, you must be able to recover to a correct, exactly-once outcome.
- **Vendor / provider abstraction** — The interface that hides a specific bank/rail behind a common port, so adding a 3rd provider is a **config change**, not a rewrite. The challenge ships **two mock fiat providers with deliberately different shapes** behind one abstraction.
- **Webhook** — An inbound HTTP callback from a provider/chain telling you something happened (e.g. a settlement). Treat them as **untrusted**: verify signatures, reject replays, and handle duplicates and out-of-order delivery.

> Anything not pinned down here is a deliberate decision for you to make. Pick a sensible default, record it in `DECISIONS.md`, and keep moving.
>