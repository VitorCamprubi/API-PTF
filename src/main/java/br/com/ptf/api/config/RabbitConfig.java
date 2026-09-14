package br.com.ptf.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do broker declarada em codigo.
 *
 * A fila principal agora aponta para uma dead letter exchange. Quando o RabbitMQ
 * recebe uma rejeicao definitiva de uma mensagem desta fila, em vez de descartar,
 * ele a reencaminha para a DLX, que a entrega na fila de mortas. Nada se perde:
 * o que nao pode ser processado fica guardado para alguem olhar.
 *
 * ATENCAO OPERACIONAL: argumentos de fila sao imutaveis no RabbitMQ. Uma fila que
 * ja existe sem x-dead-letter-exchange NAO passa a ter esse argumento por
 * redeclaracao; o broker recusa com PRECONDITION_FAILED. Em producao isso se
 * resolve criando uma fila nova e migrando os consumidores. No ambiente local,
 * apagando a fila ou recriando o container.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ptf.transactions";
    public static final String QUEUE = "ptf.transactions.process";
    public static final String ROUTING_KEY = "transaction.created";

    public static final String DEAD_LETTER_EXCHANGE = "ptf.transactions.dlx";
    public static final String DEAD_LETTER_QUEUE = "ptf.transactions.process.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "transaction.created.dead";

    @Bean
    public TopicExchange transactionsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue transactionProcessQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding transactionProcessBinding(Queue transactionProcessQueue,
                                             TopicExchange transactionsExchange) {
        return BindingBuilder.bind(transactionProcessQueue)
                .to(transactionsExchange)
                .with(ROUTING_KEY);
    }

    /**
     * Direct e nao topic: a DLX tem um destino unico e nao precisa de roteamento
     * por padrao de chave.
     */
    @Bean
    public DirectExchange transactionsDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    /**
     * A fila de mortas nao tem consumidor, e isso e intencional.
     *
     * Ela e um deposito para inspecao humana. Ligar um consumidor automatico nela
     * costuma ser a forma mais rapida de transformar um problema visivel num
     * problema invisivel: a mensagem volta para o mesmo codigo que ja falhou, e o
     * unico resultado e o erro acontecer de novo, mais tarde e com menos contexto.
     */
    @Bean
    public Queue transactionDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding transactionDeadLetterBinding(Queue transactionDeadLetterQueue,
                                                DirectExchange transactionsDeadLetterExchange) {
        return BindingBuilder.bind(transactionDeadLetterQueue)
                .to(transactionsDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * JSON em vez da serializacao Java nativa, que e o padrao do Spring AMQP.
     * Serializacao Java amarra produtor e consumidor a mesma classe, a mesma
     * versao, e a mesma linguagem. JSON e legivel no painel do broker e nao
     * quebra quando um campo e adicionado.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
