package br.com.ptf.api.exception;

public class IdempotencyConflictException extends ConflictException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("a chave de idempotencia '" + idempotencyKey
                + "' ja foi usada com um payload diferente");
    }
}
