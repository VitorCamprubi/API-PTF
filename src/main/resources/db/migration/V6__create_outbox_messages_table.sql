CREATE TABLE outbox_messages (
    id             UUID          PRIMARY KEY,
    aggregate_type VARCHAR(50)   NOT NULL,
    aggregate_id   VARCHAR(100)  NOT NULL,
    routing_key    VARCHAR(100)  NOT NULL,
    payload        VARCHAR(2000) NOT NULL,
    attempts       INTEGER       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

-- Indice parcial: so as linhas que ainda importam.
--
-- A consulta do publicador e sempre "o que ainda nao foi publicado". A tabela vai
-- crescer para milhoes de linhas publicadas que nunca mais serao lidas, e um
-- indice completo cobraria espaco e escrita por todas elas. O WHERE no indice faz
-- o Postgres guardar apenas as pendentes, que num sistema saudavel sao dezenas.
CREATE INDEX idx_outbox_pendentes
    ON outbox_messages (created_at)
    WHERE published_at IS NULL;
