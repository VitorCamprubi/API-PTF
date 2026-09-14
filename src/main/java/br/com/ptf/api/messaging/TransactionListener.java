package br.com.ptf.api.messaging;

import br.com.ptf.api.config.RabbitConfig;
import br.com.ptf.api.exception.BusinessException;
import br.com.ptf.api.service.TransactionProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor da fila, agora com duas politicas de erro.
 *
 * A pergunta que separa as duas: TENTAR DE NOVO RESOLVERIA?
 *
 * Saldo insuficiente nao resolve. Tentar mil vezes vai dar saldo insuficiente mil
 * vezes, so que gastando CPU e enchendo o log. Isso e falha PERMANENTE, tratada
 * aqui mesmo: marca o lancamento como FAILED, com motivo, e confirma a mensagem.
 *
 * Banco fora do ar, deadlock, timeout de rede: resolve, sim, e em geral em
 * segundos. Isso e falha TRANSITORIA, e a excecao sobe. O Spring AMQP entao
 * reentrega a mensagem ao proprio metodo algumas vezes, com intervalo crescente.
 * Se depois das tentativas o problema continuar, a mensagem e rejeitada em
 * definitivo e o broker a manda para a dead letter queue.
 *
 * Tratar tudo igual e o erro comum dos dois lados: quem tenta de novo o que nunca
 * vai dar certo constroi um laco infinito; quem desiste na primeira falha perde
 * dinheiro por causa de um blip de rede.
 *
 * O criterio aqui e o tipo da excecao, e e por isso que BusinessException existe
 * como familia desde a etapa 5: ela significa "a regra de negocio disse nao", que
 * e por definicao uma resposta estavel.
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

        } catch (BusinessException e) {
            log.warn("transacao {} recusada em definitivo: {}", message.transactionId(), e.getMessage());
            processingService.markFailed(message.transactionId(), e.getMessage());

        } catch (Exception e) {
            log.error("falha transitoria ao processar a transacao {}, sera retentada: {}",
                    message.transactionId(), e.getMessage());
            throw e;
        }
    }
}
