package br.com.ptf.api.domain;

/**
 * Estado do processamento de uma transacao.
 *
 * Este enum so existe porque o processamento virou assincrono. No modelo sincrono
 * anterior nao havia estado intermediario: ou a transacao existia e o saldo ja
 * tinha mudado, ou nada existia. Agora existe um intervalo entre "aceitamos o
 * pedido" e "o dinheiro se moveu", e esse intervalo precisa de nome.
 */
public enum TransactionStatus {

    /** Aceita e registrada. O saldo ainda nao mudou. */
    PENDING,

    /** Aplicada ao saldo. Estado final. */
    PROCESSED,

    /** Recusada no processamento. Estado final; failureReason diz o motivo. */
    FAILED
}
