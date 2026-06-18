CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE accounts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_account_id UUID REFERENCES accounts(id),
    is_omnibus        BOOLEAN NOT NULL DEFAULT false,
    client_name       VARCHAR(255) NOT NULL,
    currency          VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_accounts_parent ON accounts(parent_account_id);

CREATE TABLE transfers (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key    VARCHAR(512) NOT NULL,
    direction          VARCHAR(20) NOT NULL,   -- inbound, outbound, internal
    type               VARCHAR(10) NOT NULL,   -- fiat, crypto
    status             VARCHAR(20) NOT NULL DEFAULT 'pending',
    account_id         UUID NOT NULL REFERENCES accounts(id),
    amount_minor_units BIGINT NOT NULL CHECK (amount_minor_units > 0),
    currency           VARCHAR(10) NOT NULL,
    chain              VARCHAR(50),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_idempotency_key UNIQUE (idempotency_key)
);
CREATE INDEX idx_transfers_account_id ON transfers(account_id);
CREATE INDEX idx_transfers_status     ON transfers(status);

-- Append-only. No UPDATE, no DELETE ever. Sole exception: confirmed flag upgrade (ADR-008).
CREATE TABLE entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL REFERENCES transfers(id),
    account_id  UUID NOT NULL REFERENCES accounts(id),
    direction   VARCHAR(10) NOT NULL,  -- debit, credit
    amount      BIGINT NOT NULL CHECK (amount > 0),
    currency    VARCHAR(10) NOT NULL,
    confirmed   BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata    TEXT
);
CREATE INDEX idx_entries_account_currency ON entries(account_id, currency);
CREATE INDEX idx_entries_transfer_id      ON entries(transfer_id);
CREATE INDEX idx_entries_unconfirmed      ON entries(confirmed) WHERE confirmed = false;

CREATE TABLE payout_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id     UUID NOT NULL REFERENCES transfers(id),
    idempotency_key VARCHAR(512) NOT NULL,
    provider        VARCHAR(100) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    attempts        INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload         TEXT NOT NULL,
    result          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payout_jobs_transfer    UNIQUE (transfer_id),
    CONSTRAINT uq_payout_jobs_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_payout_jobs_status_next ON payout_jobs(status, next_attempt_at)
    WHERE status IN ('pending', 'processing');

CREATE TABLE webhook_events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     VARCHAR(512) NOT NULL,
    provider     VARCHAR(100) NOT NULL,
    payload      TEXT NOT NULL,
    processed_at TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_webhook_events_event_id UNIQUE (event_id)
);

CREATE TABLE routes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id       UUID NOT NULL REFERENCES accounts(id),
    name             VARCHAR(255) NOT NULL,
    is_active        BOOLEAN NOT NULL DEFAULT true,
    destination_type VARCHAR(50) NOT NULL,
    destination_ref  VARCHAR(512) NOT NULL,
    amount_strategy  VARCHAR(50) NOT NULL DEFAULT 'fixed',
    amount_value     BIGINT NOT NULL,
    provider         VARCHAR(100),
    currency         VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
