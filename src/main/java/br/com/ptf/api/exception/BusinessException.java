package br.com.ptf.api.exception;

/**
 * Base de todas as excecoes de regra de negocio.
 * Nao conhece HTTP: a traducao para status code e responsabilidade do GlobalExceptionHandler.
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }
}
