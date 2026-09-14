package br.com.ptf.api.service;

import br.com.ptf.api.domain.OutboxMessage;
import br.com.ptf.api.repository.OutboxMessageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a intencao de publicar, junto com o dado de negocio.
 *
 * MANDATORY pelo mesmo motivo do AuditService, e aqui a exigencia e ainda mais
 * forte: uma linha de outbox gravada fora da transacao do lancamento perderia
 * exatamente a propriedade que justifica a tabela existir. Se alguem chamar isto
 * sem transacao aberta, tem que estourar, nao gravar.
 */
@Service
public class OutboxService {

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxMessageRepository outboxMessageRepository, ObjectMapper objectMapper) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String aggregateType, String aggregateId, String routingKey, Object evento) {
        try {
            String payload = objectMapper.writeValueAsString(evento);
            outboxMessageRepository.save(
                    new OutboxMessage(aggregateType, aggregateId, routingKey, payload));
        } catch (JsonProcessingException e) {
            // Serializar o proprio evento nao pode falhar em runtime normal. Se
            // falhar, e bug de programacao, e derrubar a transacao inteira e o
            // comportamento certo: melhor nao registrar o lancamento do que
            // registrar um que ninguem vai processar.
            throw new IllegalStateException("falha ao serializar evento para a outbox", e);
        }
    }
}
