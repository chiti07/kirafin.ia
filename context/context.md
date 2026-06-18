# Candidate · Overview & Rules (send with Day 1)

> **5 days. ~2–3 hours/day. Design it, build it, deploy it live, then present it.**
>

## 1. Who we are

**Kira Fintech** ([kirafin.ai](http://kirafin.ai)) builds **US-based Virtual Account infrastructure**. Behind a single clean API, we coordinate partner banks and move money across **fiat rails** (ACH, Wire, SWIFT, FedNow) and **stablecoins** (USDC and USDT on Solana, Polygon, and Tron).

The hard part isn't moving money. It's **keeping the books perfect while money crosses rails that settle at different speeds, in different units, with different failure modes** — and never losing, duplicating, or misplacing a single cent.

Terms you'll need (the rest is in the **Glossary** we'll share):

- **Account** — USD-denominated, for a Client or Sub-Client. Holds balances and inbound instructions.
- **Transfer** — money in/out of an account. Direction (`inbound`/`outbound`/`internal`) + type (`fiat` or `crypto`).
- **Ramp** — **off-ramp**: stablecoin in → USD credited. **on-ramp**: USD → stablecoin out.
- **Route** — a standing rule: "when X arrives, automatically send Y."
- **Fees** — platform fee (% by volume) + fixed pass-through + optional client markup, always itemized.

## 2. The mission

Build the **ledger and orchestration engine** behind Kira's API for our client **Northwind Coffee Co.** — and make it real enough that we can open a URL and watch money move.

The flow you must support, end to end:

> A counterparty sends **5,000 USDC on Solana**. You detect it on-chain, wait for confirmations, run the **off-ramp** (apply fees, credit USD). A **route fires** and pays a vendor **$4,200 via ACH**; a second payout sends **600 USDT on Polygon**. Every step is validated, idempotent, and reconciled at end of day.
>

You own the whole thing: **backend, full-stack UI, Web3 (real testnet), and deployment.** No optional tracks. We're hiring someone who ships a feature end to end.

## 3. The rules that don't bend

Everything else is your call. These are not negotiable:

- **No negative balances. Ever.** Even under a flood of concurrent payouts. Enforce it where it can't race.
- **Idempotent everything.** A retried request or a webhook delivered twice must never move money twice.
- **No floating-point money.** Integer minor units or high-precision decimals only. A `float` on a balance is an automatic deduction.

> ⚠️ **Heads up**
Three things will bite you if you ignore them: **per-chain decimals** differ, **a deposit isn't money until confirmed** (reorgs are real), and **crashes happen between the ledger write and the provider call.** Design for all three. *Fiat rails are mocked (give us two providers with different shapes behind one abstraction); the crypto leg is real on testnet.*
>

We'll hand you a glossary as documentation. Everything else — the repo, the stack, the infrastructure, the local `docker-compose` — you build from scratch. The interesting decisions are yours to make — and to defend.

## 4. How the week is structured

We release **one brief per day** — you won't get the whole week on Day 1. That's deliberate: we want to see how you plan with what you have. The three deliverables:

| Days | Deliverable | The question |
| --- | --- | --- |
| 1–2 | **Plan & Understanding** | Do you get the problem before you touch code? *(We give no feedback at this stage — on purpose.)* |
| 3–4 | **Technical Build — deployed live** | Does it work end-to-end, on a URL we can open? |
| 5 | **Final Presentation** (50-min call) | Can you make us trust the system? |

## 5. Using AI — we encourage it

Use whatever makes you most effective, **including AI assistants** — that's how we work here. We care about the judgment and the result, not whether you typed every line.

If you use AI, **own the output**: understand it, validate it against your own tests and the rules above, and be ready to explain where it helped, where it was wrong, and where you overrode it. The strongest candidates treat AI like a sharp junior engineer — they direct it, review it, and stay accountable for what ships. On the final call we may ask you to use it live.

## 6. How we score (summary)

An automated agent first checks the objective guardrails (no floats, idempotency, no negative balances). The team then judges architecture, the design doc, testing, domain, infrastructure, security, your final presentation, and the live change. We measure **correctness per unit of complexity** — not lines of code. **Exceptional work beyond the required scope earns a bonus on top of 100%** (see your Day 4/5 briefs for what counts).

## 7. Delivering

A **Git repo** (we read commit history). A `README.md` with one-command setup, how to run the tests, and the **live URL**. A `.env.example` (never commit secrets) and seed data that reproduces the Northwind flow. Keep a `DESIGN.md` and a running `DECISIONS.md` (lightweight ADR-style) — we read both.

> If something here is unclear, that's part of the test. Make a call, write it down in `DECISIONS.md`, and keep moving. We're excited to see how you think about money. 🚀
>