package br.com.ptf.api.metrics;

import br.com.ptf.api.domain.TransactionType;
import br.com.ptf.api.repository.OutboxMessageRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Metricas de negocio do processamento de transacoes.
 *
 * As metricas tecnicas (latencia HTTP, pool de conexoes, memoria, GC) o Spring
 * Boot ja publica sozinho. Estas aqui respondem perguntas que so este dominio
 * tem: quantos lancamentos entraram, quantos foram recusados, quanto tempo leva
 * entre aceitar e aplicar, e se a outbox esta acumulando.
 *
 * SOBRE CARDINALIDADE, que e o erro classico de quem esta comecando com metricas:
 * cada combinacao distinta de valores de tag vira uma serie temporal separada,
 * guardada para sempre no servidor de metricas. Uma tag com o id da transacao, ou
 * com a mensagem de erro, geraria milhoes de series e derrubaria o Prometheus
 * antes de responder qualquer pergunta util. Por isso as tags aqui so tem valores
 * de conjunto fechado e pequeno: o tipo (dois valores) e a causa da falha (dois
 * valores). Id e mensagem sao assunto de log, nao de metrica.
 */
@Component
public class TransactionMetrics {

    public static final String CAUSA_NEGOCIO = "business";
    public static final String CAUSA_TRANSITORIA = "transient";

    /**
     * "accepted" e nao "created", e o motivo nao e estetico.
     *
     * No formato de exposicao do Prometheus, "_created" e um sufixo RESERVADO:
     * no OpenMetrics ele marca o instante de criacao de um contador. Uma metrica
     * terminada em "created" e sanitizada pelo cliente, que remove o sufixo antes
     * de acrescentar o "_total" dele. Na pratica, ptf.transactions.created era
     * publicada como ptf_transactions_total, silenciosamente, sem erro nenhum.
     *
     * O nome novo ainda por cima descreve melhor o que acontece: a requisicao
     * responde 202 Accepted, e o que este contador conta e aceitacao, nao criacao
     * de dinheiro.
     *
     * Licao geral: nome de metrica nao e so nome. Evite terminar em _created,
     * _total, _sum, _count ou _bucket, que sao sufixos do protocolo.
     */
    private static final String ACEITAS = "ptf.transactions.accepted";
    private static final String PROCESSADAS = "ptf.transactions.processed";
    private static final String FALHAS = "ptf.transactions.failed";
    private static final String DURACAO = "ptf.transactions.processing";

    private final MeterRegistry registry;

    public TransactionMetrics(MeterRegistry registry, OutboxMessageRepository outboxMessageRepository) {
        this.registry = registry;

        /*
         * Gauge, e nao counter: contador so sobe e serve para "quantos ja
         * aconteceram"; medidor mostra um valor instantaneo que sobe e desce. A
         * fila da outbox e exatamente isso, e o alarme mais util do sistema mora
         * aqui: se este numero cresce e nao volta, o publicador parou, e nenhuma
         * transacao esta chegando ao broker.
         *
         * O preco e uma consulta ao banco a cada raspagem do Prometheus. Com
         * intervalo de 15 segundos e um indice parcial cobrindo a consulta, e
         * barato o suficiente.
         */
        Gauge.builder("ptf.outbox.pending", outboxMessageRepository,
                        repositorio -> repositorio.countByPublishedAtIsNull())
                .description("mensagens na outbox ainda nao publicadas no broker")
                .register(registry);
    }

    public void contarCriada(TransactionType tipo) {
        registry.counter(ACEITAS, "type", tipo.name()).increment();
    }

    public void contarProcessada(TransactionType tipo) {
        registry.counter(PROCESSADAS, "type", tipo.name()).increment();
    }

    public void contarFalha(String causa) {
        registry.counter(FALHAS, "cause", causa).increment();
    }

    public Timer.Sample iniciarCronometro() {
        return Timer.start(registry);
    }

    /**
     * Mede o tempo de processamento no consumidor, com histograma.
     *
     * O histograma existe para permitir percentis. Media de latencia e uma das
     * estatisticas mais enganosas que existem em sistema: com 99 respostas de 10ms
     * e uma de 3 segundos, a media da 40ms e ninguem descobre o cliente que
     * esperou tres segundos. p99 conta.
     */
    public void registrarDuracao(Timer.Sample amostra, String resultado) {
        amostra.stop(Timer.builder(DURACAO)
                .description("tempo entre o consumidor pegar a mensagem e concluir")
                .tag("outcome", resultado)
                .publishPercentileHistogram()
                .register(registry));
    }
}
