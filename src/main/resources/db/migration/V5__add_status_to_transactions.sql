ALTER TABLE transactions ADD COLUMN status         VARCHAR(20);
ALTER TABLE transactions ADD COLUMN processed_at   TIMESTAMPTZ;
ALTER TABLE transactions ADD COLUMN failure_reason VARCHAR(255);

-- Toda transacao que ja existe foi criada e processada no mesmo instante, porque
-- ate agora o saldo era aplicado de forma sincrona. Adicionar coluna NOT NULL em
-- tabela com dados exige decidir o que fazer com o passado: aqui, dizer a verdade
-- sobre ele. Sem este UPDATE, o ALTER seguinte falharia.
UPDATE transactions SET status = 'PROCESSED', processed_at = created_at;

ALTER TABLE transactions ALTER COLUMN status SET NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'));

CREATE INDEX idx_transactions_status ON transactions (status) WHERE status = 'PENDING';
