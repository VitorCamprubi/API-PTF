package br.com.ptf.api.exception;

import java.util.UUID;

public class AccountNotFoundException extends NotFoundException {

    public AccountNotFoundException(UUID id) {
        super("conta nao encontrada: " + id);
    }
}
