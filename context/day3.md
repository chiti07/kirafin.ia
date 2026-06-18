Candidate · Day 3 — Build the core, live
Deliverable 2 (Technical Build) · Part 1 of 2 · ~2–3 hrs
Code starts. Stand the engine up and get it deploying early — don't leave deploy for the last hour.
Focus
Build the functional core of the engine and get a deploy pipeline working while it's still small.
Ledger + off-ramp. Implement the double-entry ledger and the off-ramp: detect an inbound USDC deposit on real testnet (Solana devnet / Polygon Amoy), wait for confirmations, credit USD with itemized fees. Pending vs. available must be distinct.
Idempotency & guardrails, for real. Enforce no-negative-balance where it can't race, and make inbound/outbound idempotent. These are the things the automated agent will probe hardest.
Deploy thin and early. Get the skeleton onto a public URL via IaC, with a staging/prod split. A tiny system that deploys beats a big one that doesn't.
Build for real traffic, security, and scale (start here)
This is production-shaped infrastructure, so design it like it. Begin addressing — and note your choices in DECISIONS.md:
Traffic & scalability. The system must stay correct under concurrent load (a burst of simultaneous payouts against the same balance). Show how your design scales — async workers, a queue, where state lives, how idempotency holds under load. Tell us what would fall over first at 10× traffic.
Security. Authn/authz boundary on the API, secrets via env (never committed), validated inputs, and a plan for signed/verified inbound webhooks (a tampered or replayed webhook must be rejected).
✅ Definition of Done — Day 3

The ledger + off-ramp run, with idempotency and no-negative-balance enforced structurally (not by convention).

The crypto leg works against a real testnet with a confirmation threshold before crediting available balance.

The skeleton is reachable on a public URL via reproducible IaC, with a staging/prod separation.

DECISIONS.md records your concurrency/scalability approach and your security boundary.
