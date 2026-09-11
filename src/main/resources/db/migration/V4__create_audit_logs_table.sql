CREATE TABLE audit_logs (
    id          UUID         PRIMARY KEY,
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   VARCHAR(100) NOT NULL,
    detail      VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- A consulta de auditoria e sempre "o que aconteceu com esta entidade, do mais
-- recente para o mais antigo".
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at DESC);

-- Nao ha CHECK listando as acoes possiveis, ao contrario de transactions.type.
-- A diferenca e de natureza: tipo de transacao e invariante financeiro e muda
-- quase nunca; acao auditada e catalogo descritivo e cresce a cada funcionalidade
-- nova. Um CHECK aqui obrigaria uma migration por linha de log nova, sem proteger
-- nenhum dinheiro.
