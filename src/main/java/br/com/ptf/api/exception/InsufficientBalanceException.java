package br.com.ptf.api.exception;

import java.math.BigDecimal;

/**
 * Saldo insuficiente para a operacao pedida.
 *
 * Estende ConflictException porque, se um dia um endpoint sincrono precisar dela,
 * 409 e a traducao correta: o pedido esta bem formado, e o estado atual do recurso
 * e que nao permite. Hoje ela nunca chega ao HTTP; morre no consumidor e vira o
 * failureReason do lancamento.
 *
 * A mensagem cita o valor pedido, que veio do proprio cliente, e nao o saldo da
 * conta. Motivo do lancamento vai para log e auditoria, e saldo alheio nao precisa
 * estar espalhado por ai.
 */
public class InsufficientBalanceException extends ConflictException {

    public InsufficientBalanceException(BigDecimal amount) {
        super("saldo insuficiente para debitar " + amount);
    }
}
