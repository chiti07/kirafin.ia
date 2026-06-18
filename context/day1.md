# Candidate · Day 1 — Understand the problem

> **Deliverable 1 (Plan & Understanding) · Part 1 of 2 · ~2–3 hrs**
Today is about understanding, not implementation. No code required yet.
>

## Focus

Read the Overview and the glossary. Then start your **design document** (`DESIGN.md`). We want to see that you grasp the *business* problem — perfect books across rails that settle differently — before you model a single table.

Work through, in writing:

- **The domain in your words.** What is an Account, a Transfer, a Ramp, a Route, a Fee — and how does the Northwind flow move through them? Frame the business problem, not just the API.
- **The ledger model.** How would you model a **double-entry, append-only** ledger where balances are *derived* (not mutated) and fees are themselves entries? Sketch the core tables/relationships.
- **Where money can't race.** Name the point(s) where a negative balance or a double-spend could occur, and your first instinct for enforcing the invariant where it can't race.

> 📝 **Note**
Capture open questions and assumptions in `DECISIONS.md` as you go. We won't answer them now (see Day 2) — making a reasoned call *is* the signal.
>

✅ **Definition of Done — Day 1**

- [ ]  `DESIGN.md` exists and explains the domain in business terms, not just endpoints.
- [ ]  A first-pass **double-entry** data model is sketched: accounts, entries/postings, balance-as-derived, fees-as-entries.
- [ ]  You've named at least one concurrency/consistency hazard and your intended guardrail.
- [ ]  `DECISIONS.md` is started, with your assumptions written down.
- [ ]  Money is conceived as integer minor units / decimal — never float — and you note per-chain decimal differences.