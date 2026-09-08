package br.com.ptf.api.exception;

import java.util.UUID;

public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException(UUID id) {
        super("conta nao encontrada: " + id);
    }
}
