package br.com.ptf.api.messaging;

import br.com.ptf.api.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica no broker DEPOIS do commit, nunca durante.
 *
 * Este e o detalhe mais facil de errar da etapa. Se a mensagem fosse enviada
 * dentro da transacao, o consumidor poderia receber o id e ir buscar no banco uma
 * linha que ainda nao foi commitada: ele leria "transacao nao encontrada" e
 * descartaria um lancamento valido. Pior ainda quando a transacao acaba dando
 * rollback: a fila teria uma ordem para processar algo que nunca existiu.
 *
 * @TransactionalEventListener com phase AFTER_COMMIT resolve isso: o service
 * publica um evento interno de aplicacao, o Spring segura, e so entrega aqui
 * quando o commit terminou.
 *
 * O que ainda NAO esta resolvido: se a aplicacao morrer entre o commit e este
 * envio, a transacao fica PENDING para sempre, porque a mensagem nunca saiu. Sao
 * dois sistemas diferentes sendo escritos sem uma transacao comum, e nenhum
 * ajuste de ordem elimina isso. E o problema do dual write, e a solucao dele e o
 * transactional outbox da etapa 14.
 */
@Component
public class TransactionPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public TransactionPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TransactionMessage message) {
        log.debug("publicando transacao {}", message.transactionId());
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
    }
}
