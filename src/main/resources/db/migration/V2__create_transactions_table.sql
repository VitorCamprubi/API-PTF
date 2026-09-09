CREATE TABLE transactions (
    id          UUID           PRIMARY KEY,
    account_id  UUID           NOT NULL REFERENCES accounts (id),
    type        VARCHAR(10)    NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_type CHECK (type IN ('CREDIT', 'DEBIT'))
);

-- O Postgres nao cria indice automatico para chave estrangeira. Sem este,
-- qualquer consulta de transacoes por conta vira sequential scan na tabela toda.
CREATE INDEX idx_transactions_account_id_created_at
    ON transactions (account_id, created_at DESC);
