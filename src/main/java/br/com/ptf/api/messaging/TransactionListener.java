package br.com.ptf.api.messaging;

import br.com.ptf.api.config.RabbitConfig;
import br.com.ptf.api.service.TransactionProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor da fila.
 *
 * Repare no que este metodo NAO tem: @Transactional. De proposito. Se ele fosse
 * transacional, a falha no processamento derrubaria a mesma transacao em que a
 * marcacao de FAILED seria gravada, e o lancamento ficaria PENDING para sempre,
 * sem nenhum registro do motivo. Sao duas transacoes distintas: uma tenta
 * aplicar, outra registra que nao deu.
 *
 * O try/catch tambem tem funcao de protocolo, nao so de log. Se a excecao
 * escapasse daqui, o Spring AMQP devolveria a mensagem para a fila, e ela seria
 * entregue de novo, e de novo, para sempre: e a poison message, que consome CPU
 * eternamente processando algo que nunca vai dar certo. Tratar a falha aqui faz a
 * mensagem ser confirmada. Retry de verdade, com limite e fila de descarte, e a
 * etapa 15.
 */
@Component
public class TransactionListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionListener.class);

    private final TransactionProcessingService processingService;

    public TransactionListener(TransactionProcessingService processingService) {
        this.processingService = processingService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onTransactionCreated(TransactionMessage message) {
        try {
            processingService.process(message.transactionId());
        } catch (Exception e) {
            log.warn("falha ao processar a transacao {}: {}", message.transactionId(), e.getMessage());
            processingService.markFailed(message.transactionId(), e.getMessage());
        }
    }
}
