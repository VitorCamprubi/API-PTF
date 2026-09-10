package br.com.ptf.api.exception;

/**
 * Estado atual do sistema nao permite a operacao pedida.
 *
 * Mesma ideia da NotFoundException: agrupa o que vira 409 para o handler traduzir
 * uma vez so.
 */
public abstract class ConflictException extends BusinessException {

    protected ConflictException(String message) {
        super(message);
    }
}
