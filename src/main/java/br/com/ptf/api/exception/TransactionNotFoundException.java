package br.com.ptf.api.exception;

import java.util.UUID;

public class TransactionNotFoundException extends NotFoundException {

    public TransactionNotFoundException(UUID id) {
        super("transacao nao encontrada: " + id);
    }
}
