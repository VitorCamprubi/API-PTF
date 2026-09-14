package br.com.ptf.api.messaging;

import br.com.ptf.api.config.RabbitConfig;
import br.com.ptf.api.domain.OutboxMessage;
import br.com.ptf.api.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Le a outbox e entrega ao broker.
 *
 * O lote inteiro roda numa transacao so: as linhas ficam travadas do SELECT ate o
 * commit, e a marcacao de publicado vai junto.
 *
 * O que esta desenho garante e "pelo menos uma vez", nunca "exatamente uma vez".
 * Se o envio ao RabbitMQ der certo e o commit falhar logo depois, a linha continua
 * pendente e sera enviada de novo no proximo ciclo. Isso nao e defeito do outbox,
 * e a consequencia inevitavel de dois sistemas sem transacao comum: da para
 * escolher entre perder mensagem e duplicar mensagem, e duplicar e o lado
 * consertavel. Quem conserta e o consumidor, com a checagem de isPending que
 * existe desde a etapa 12.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxMessageRepository outboxMessageRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxMessageRepository outboxMessageRepository, RabbitTemplate rabbitTemplate) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public int publicarLote(int tamanhoDoLote) {
        List<OutboxMessage> pendentes = outboxMessageRepository.travarPendentes(tamanhoDoLote);

        for (OutboxMessage pendente : pendentes) {
            try {
                rabbitTemplate.send(RabbitConfig.EXCHANGE, pendente.getRoutingKey(), montar(pendente));
                pendente.markPublished();
            } catch (Exception e) {
                // Nao publicou: a linha fica pendente e o proximo ciclo tenta de
                // novo. Uma mensagem problematica nao pode impedir as outras do
                // lote de sairem, por isso o catch e por item e nao por lote.
                pendente.markAttemptFailed();
                log.warn("falha ao publicar a mensagem {} da outbox (tentativa {}): {}",
                        pendente.getId(), pendente.getAttempts(), e.getMessage());
            }
        }

        return pendentes.size();
    }

    /**
     * O payload ja esta serializado em JSON no banco, entao ele vai como bytes,
     * com o content type declarado. Passar a string por um conversor de novo
     * produziria um JSON dentro de um JSON.
     *
     * Sem cabecalho de tipo: o consumidor descobre a classe pela assinatura do
     * metodo anotado com @RabbitListener. Isso mantem a mensagem legivel no painel
     * do broker e desacoplada do nome das classes Java deste projeto.
     */
    private static Message montar(OutboxMessage pendente) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propriedades.setMessageId(pendente.getId().toString());

        return new Message(pendente.getPayload().getBytes(StandardCharsets.UTF_8), propriedades);
    }
}
