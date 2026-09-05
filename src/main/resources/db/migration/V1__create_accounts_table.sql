CREATE TABLE accounts (
    id          UUID           PRIMARY KEY,
    document    VARCHAR(14)    NOT NULL UNIQUE,
    holder_name VARCHAR(150)   NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
);
