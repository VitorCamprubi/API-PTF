package br.com.ptf.api.exception;

/**
 * Recurso pedido nao existe.
 *
 * Extraida para o handler traduzir "nao encontrado" uma vez so, em vez de
 * ganhar um metodo novo a cada entidade nova do projeto.
 */
public abstract class NotFoundException extends BusinessException {

    protected NotFoundException(String message) {
        super(message);
    }
}
