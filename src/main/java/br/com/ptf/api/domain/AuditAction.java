package br.com.ptf.api.domain;

public enum AuditAction {

    ACCOUNT_CREATED,
    TRANSACTION_CREATED,
    TRANSACTION_REPLAYED,
    IDEMPOTENCY_CONFLICT
}
