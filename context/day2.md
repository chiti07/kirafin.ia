Candidate · Day 2 — Finalize the plan
Deliverable 1 (Plan & Understanding) · Part 2 of 2 · ~2–3 hrs
Finish the design doc. This is your last design-only day — build starts tomorrow.
Focus
Turn yesterday's sketch into a design a teammate could build from. Show the thinking, not the implementation.
Abstraction & boundaries. Define the vendor abstraction so a 3rd bank provider would be a config change, not a rewrite. Draw the API / domain / worker boundaries. Two mock fiat providers with different shapes must sit behind one interface.
Failure & crash-consistency. Name the crash window between the ledger write and the provider call, and exactly how you recover. Define your idempotency keys precisely — what makes a retried request or a twice-delivered webhook safe.
Reconciliation as a query. Explain how end-of-day reconciliation against a bank/chain statement is just a query over your append-only entries, and what the two kinds of mismatch are (settled-with-no-entry; entry-never-confirmed).
Trade-offs. State the calls you're making and why — and what you're explicitly not doing.
✅ Definition of Done — Day 2 (Deliverable 1 complete)

DESIGN.md is complete: data model, idempotency strategy, crash-recovery, vendor abstraction, reconciliation approach, and named trade-offs.

A reviewer could understand your system and its invariants without any code.

DECISIONS.md reflects the key choices and the assumptions behind them.

Submit Deliverable 1 (push to the repo) by end of your Day 2 window.
📝 Note
No feedback yet — on purpose. Deliverable 1 must reflect your own judgment, unaided. You'll get our feedback after Deliverable 2, so commit to your design and run with it.
