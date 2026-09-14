package br.com.ptf.api.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara o publicador de tempos em tempos.
 *
 * Duas classes e nao uma, e o motivo e o mesmo de sempre: o Spring aplica
 * @Transactional por proxy. Se o metodo agendado chamasse um metodo transacional
 * da propria classe, a chamada nao passaria pelo proxy e nao haveria transacao
 * nenhuma; as linhas seriam lidas sem trava e a marcacao de publicado nao seria
 * gravada. Chamando outro bean, a chamada passa pelo proxy dele.
 *
 * fixedDelay e nao fixedRate: o intervalo conta a partir do FIM da execucao
 * anterior. Com fixedRate, um lote lento faria as execucoes se acumularem umas
 * sobre as outras.
 */
@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxPublisher outboxPublisher;
    private final int tamanhoDoLote;

    public OutboxScheduler(OutboxPublisher outboxPublisher,
                           @Value("${ptf.outbox.tamanho-do-lote:50}") int tamanhoDoLote) {
        this.outboxPublisher = outboxPublisher;
        this.tamanhoDoLote = tamanhoDoLote;
    }

    @Scheduled(fixedDelayString = "${ptf.outbox.intervalo-ms:500}")
    public void publicar() {
        int publicadas = outboxPublisher.publicarLote(tamanhoDoLote);
        if (publicadas > 0) {
            log.debug("outbox: {} mensagens processadas no ciclo", publicadas);
        }
    }
}
