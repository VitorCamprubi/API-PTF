package br.com.ptf.api;

import br.com.ptf.api.config.RabbitConfig;
import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.TransactionStatus;
import br.com.ptf.api.domain.TransactionType;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeadLetterIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    /**
     * TRUNCATE limpa tabela, nao fila. Sobra de mensagem morta de um teste
     * anterior faria o teste seguinte passar por motivo errado.
     */
    @BeforeEach
    void esvaziarFilaDeMortas() {
        while (rabbitTemplate.receive(RabbitConfig.DEAD_LETTER_QUEUE, 100) != null) {
            // descarta
        }
    }

    @Test
    @DisplayName("mensagem ilegivel termina na dead letter queue")
    void mensagemIlegivelVaiParaDlq() {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY,
                "isto aqui nao e uma transacao");

        Message morta = rabbitTemplate.receive(RabbitConfig.DEAD_LETTER_QUEUE, 10_000);

        assertThat(morta)
                .as("o que o consumidor nao consegue sequer interpretar tem que ser guardado, nao descartado")
                .isNotNull();
    }

    /**
     * O contraste que prova o desenho.
     *
     * Saldo insuficiente tambem e uma falha, mas de outra natureza: a regra de
     * negocio respondeu, e a resposta nao muda por tentar de novo. O lancamento
     * termina em FAILED com o motivo registrado, e a fila de mortas continua
     * vazia, porque nao ha nada ali para um humano investigar.
     */
    @Test
    @DisplayName("falha de negocio vira FAILED e nao suja a dead letter queue")
    void falhaDeNegocioNaoVaiParaDlq() throws Exception {
        Account conta = accountRepository.save(new Account("12345678901", "Vitor Camprubi"));

        UUID debito = idDaResposta(postTransacao(conta.getId(), TransactionType.DEBIT, "500.00")
                .andExpect(status().isAccepted()));

        aguardar(() -> assertThat(transactionRepository.findById(debito).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.FAILED));

        assertThat(rabbitTemplate.receive(RabbitConfig.DEAD_LETTER_QUEUE, 1_000))
                .as("erro de regra de negocio nao e assunto de fila de mortas")
                .isNull();
    }

    /**
     * Uma mensagem venenosa nao pode derrubar o consumidor.
     *
     * Este teste existe porque o modo classico de falhar aqui e silencioso: sem
     * dead letter e com reentrega ligada, a mensagem ruim volta para a fila para
     * sempre, fica na frente das outras, e o sistema inteiro para de processar
     * sem nenhum erro chamativo aparecer.
     */
    @Test
    @DisplayName("depois de uma mensagem venenosa, o consumidor continua trabalhando")
    void consumidorSobreviveAMensagemVenenosa() throws Exception {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, "lixo");

        assertThat(rabbitTemplate.receive(RabbitConfig.DEAD_LETTER_QUEUE, 10_000)).isNotNull();

        Account conta = accountRepository.save(new Account("12345678901", "Vitor Camprubi"));
        UUID credito = idDaResposta(postTransacao(conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isAccepted()));

        aguardar(() -> {
            assertThat(transactionRepository.findById(credito).orElseThrow().getStatus())
                    .isEqualTo(TransactionStatus.PROCESSED);
            assertThat(accountRepository.findById(conta.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("100.00");
        });
    }

    private UUID idDaResposta(ResultActions resultado) throws Exception {
        String corpo = resultado.andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(corpo).get("id").asText());
    }

    private ResultActions postTransacao(UUID contaId, TransactionType tipo, String valor) throws Exception {
        return mockMvc.perform(post("/transactions")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(contaId, tipo, new BigDecimal(valor), "teste"))));
    }
}
