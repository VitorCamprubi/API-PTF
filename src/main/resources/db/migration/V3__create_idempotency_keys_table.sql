CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    transaction_id  UUID         NOT NULL REFERENCES transactions (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
