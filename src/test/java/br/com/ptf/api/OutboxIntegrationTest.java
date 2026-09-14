package br.com.ptf.api;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.OutboxMessage;
import br.com.ptf.api.domain.TransactionStatus;
import br.com.ptf.api.domain.TransactionType;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.OutboxMessageRepository;
import br.com.ptf.api.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OutboxIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Test
    @DisplayName("cada transacao criada deixa uma mensagem na outbox, que e publicada e marcada")
    void mensagemGravadaEPublicada() throws Exception {
        Account conta = novaConta();

        UUID transacaoId = idDaResposta(postTransacao(conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isAccepted()));

        List<OutboxMessage> mensagens = outboxMessageRepository.findAll();
        assertThat(mensagens)
                .as("a linha da outbox nasce na mesma transacao do lancamento")
                .hasSize(1);
        assertThat(mensagens.get(0).getAggregateId()).isEqualTo(transacaoId.toString());
        assertThat(mensagens.get(0).getPayload()).contains(transacaoId.toString());

        aguardar(() -> {
            assertThat(outboxMessageRepository.countByPublishedAtIsNull())
                    .as("o scheduler tem que drenar a outbox")
                    .isZero();
            assertThat(transactionRepository.findById(transacaoId).orElseThrow().getStatus())
                    .isEqualTo(TransactionStatus.PROCESSED);
        });

        assertThat(outboxMessageRepository.findAll().get(0).getAttempts()).isEqualTo(1);
    }

    /**
     * A propriedade que justifica a tabela existir.
     *
     * A requisicao e recusada com 409 e a transacao inteira volta atras. Se a
     * mensagem tivesse sido publicada direto no broker, ela ja teria saido e o
     * consumidor receberia ordem para processar um lancamento que nao existe. Como
     * ela e apenas uma linha na mesma transacao, o rollback a leva junto.
     */
    @Test
    @DisplayName("operacao recusada nao deixa mensagem na outbox")
    void rollbackNaoDeixaMensagem() throws Exception {
        Account conta = novaConta();
        String chave = UUID.randomUUID().toString();

        postComChave(chave, conta.getId(), "100.00").andExpect(status().isAccepted());
        postComChave(chave, conta.getId(), "200.00").andExpect(status().isConflict());

        aguardar(() -> assertThat(outboxMessageRepository.countByPublishedAtIsNull()).isZero());

        assertThat(outboxMessageRepository.findAll())
                .as("apenas a primeira requisicao gerou mensagem")
                .hasSize(1);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("varias transacoes geram varias mensagens e todas sao drenadas")
    void loteDeMensagens() throws Exception {
        Account conta = novaConta();

        for (int i = 0; i < 5; i++) {
            postTransacao(conta.getId(), TransactionType.CREDIT, "10.00")
                    .andExpect(status().isAccepted());
        }

        assertThat(outboxMessageRepository.count()).isEqualTo(5);

        aguardar(() -> {
            assertThat(outboxMessageRepository.countByPublishedAtIsNull()).isZero();
            assertThat(accountRepository.findById(conta.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("50.00");
        });
    }

    private Account novaConta() {
        return accountRepository.save(new Account("12345678901", "Vitor Camprubi"));
    }

    private UUID idDaResposta(ResultActions resultado) throws Exception {
        String corpo = resultado.andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(corpo).get("id").asText());
    }

    private ResultActions postTransacao(UUID contaId, TransactionType tipo, String valor) throws Exception {
        return mockMvc.perform(post("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(contaId, tipo, new BigDecimal(valor), "teste"))));
    }

    private ResultActions postComChave(String chave, UUID contaId, String valor) throws Exception {
        return mockMvc.perform(post("/transactions")
                .header("Idempotency-Key", chave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(
                        contaId, TransactionType.CREDIT, new BigDecimal(valor), "teste"))));
    }
}
