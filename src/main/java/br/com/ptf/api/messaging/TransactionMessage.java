package br.com.ptf.api.messaging;

import java.util.UUID;

/**
 * O que trafega na fila: apenas o identificador.
 *
 * A alternativa seria mandar a transacao inteira dentro da mensagem. Nao mandamos
 * por dois motivos. Primeiro, a mensagem vira uma copia que envelhece: se algo
 * mudar no banco entre publicar e consumir, o consumidor trabalha com dado velho.
 * Segundo, dado financeiro parado numa fila e mais uma copia para proteger. Com o
 * id, o consumidor busca a versao atual na fonte da verdade, que e o banco.
 *
 * O custo e uma leitura a mais por mensagem. Em troca, nao existe divergencia
 * possivel entre o que a fila diz e o que o banco tem.
 */
public record TransactionMessage(UUID transactionId) {
}
