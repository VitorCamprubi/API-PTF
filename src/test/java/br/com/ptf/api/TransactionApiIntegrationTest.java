package br.com.ptf.api;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.Transaction;
import br.com.ptf.api.domain.TransactionStatus;
import br.com.ptf.api.domain.TransactionType;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("credito devolve 202 com status PENDING e o saldo muda depois")
    void creditoProcessadoAssincronamente() throws Exception {
        Account conta = novaConta();

        UUID transacaoId = idDaResposta(
                postTransacao(conta.getId(), TransactionType.CREDIT, "1000.50")
                        .andExpect(status().isAccepted())
                        .andExpect(header().exists("Location"))
                        .andExpect(jsonPath("$.status").value("PENDING")));

        aguardar(() -> {
            assertThat(statusDe(transacaoId)).isEqualTo(TransactionStatus.PROCESSED);
            assertThat(saldoDe(conta.getId())).isEqualByComparingTo("1000.50");
        });
    }

    @Test
    @DisplayName("credito seguido de debito chega ao saldo correto")
    void creditoEDebito() throws Exception {
        Account conta = novaConta();

        UUID credito = idDaResposta(postTransacao(conta.getId(), TransactionType.CREDIT, "1000.50")
                .andExpect(status().isAccepted()));
        aguardar(() -> assertThat(statusDe(credito)).isEqualTo(TransactionStatus.PROCESSED));

        UUID debito = idDaResposta(postTransacao(conta.getId(), TransactionType.DEBIT, "300.25")
                .andExpect(status().isAccepted()));
        aguardar(() -> assertThat(statusDe(debito)).isEqualTo(TransactionStatus.PROCESSED));

        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("700.25");
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    /**
     * A recusa mudou de lugar.
     *
     * Antes, o debito impossivel derrubava a requisicao com 409. Agora a
     * requisicao e aceita com 202, porque no momento em que ela chega ninguem
     * ainda olhou o saldo, e a recusa acontece do outro lado da fila. O cliente
     * descobre consultando o status, e o motivo fica gravado no lancamento.
     *
     * Esse e o preco do assincrono, e a resposta honesta numa entrevista: voce
     * ganha desacoplamento e absorcao de pico, e paga com um contrato em que
     * "aceito" nao significa mais "deu certo".
     */
    @Test
    @DisplayName("debito acima do saldo e aceito, falha no processamento e nao move o saldo")
    void debitoAcimaDoSaldoTerminaEmFailed() throws Exception {
        Account conta = novaConta();

        UUID credito = idDaResposta(postTransacao(conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isAccepted()));
        aguardar(() -> assertThat(statusDe(credito)).isEqualTo(TransactionStatus.PROCESSED));

        UUID debito = idDaResposta(postTransacao(conta.getId(), TransactionType.DEBIT, "500.00")
                .andExpect(status().isAccepted()));

        aguardar(() -> assertThat(statusDe(debito)).isEqualTo(TransactionStatus.FAILED));

        Transaction falha = transactionRepository.findById(debito).orElseThrow();
        assertThat(falha.getFailureReason()).isNotBlank();
        assertThat(falha.getProcessedAt()).isNotNull();
        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("conta inexistente devolve 404 na hora, sem entrar na fila")
    void contaInexistente() throws Exception {
        postTransacao(UUID.randomUUID(), TransactionType.CREDIT, "10.00")
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    @DisplayName("valor zero devolve 400 apontando o campo")
    void valorZero() throws Exception {
        Account conta = novaConta();

        postTransacao(conta.getId(), TransactionType.CREDIT, "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("amount"));

        assertThat(transactionRepository.count()).isZero();
    }

    private Account novaConta() {
        return accountRepository.save(new Account("12345678901", "Vitor Camprubi"));
    }

    private BigDecimal saldoDe(UUID contaId) {
        return accountRepository.findById(contaId).orElseThrow().getBalance();
    }

    private TransactionStatus statusDe(UUID transacaoId) {
        return transactionRepository.findById(transacaoId).orElseThrow().getStatus();
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
}
