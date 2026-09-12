package br.com.ptf.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
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
 * O Spring cria fila, exchange e binding na subida se nao existirem. Isso tira do
 * deploy a etapa manual de "alguem entra no painel do RabbitMQ e cria a fila", que
 * e exatamente o tipo de passo que funciona no ambiente de quem escreveu e falha
 * em producao as duas da manha.
 *
 * Exchange do tipo topic em vez de enviar direto para a fila: quem publica nao
 * precisa saber quem consome. Quando a etapa 15 adicionar dead-letter, ou quando
 * um segundo consumidor quiser o mesmo evento, muda-se o binding sem tocar em
 * quem publica.
 *
 * Tudo durable: fila e exchange sobrevivem a um restart do broker. Mensagem de
 * dinheiro que some quando o RabbitMQ reinicia nao serve.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "ptf.transactions";
    public static final String QUEUE = "ptf.transactions.process";
    public static final String ROUTING_KEY = "transaction.created";

    @Bean
    public TopicExchange transactionsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue transactionProcessQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding transactionProcessBinding(Queue transactionProcessQueue,
                                             TopicExchange transactionsExchange) {
        return BindingBuilder.bind(transactionProcessQueue)
                .to(transactionsExchange)
                .with(ROUTING_KEY);
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
