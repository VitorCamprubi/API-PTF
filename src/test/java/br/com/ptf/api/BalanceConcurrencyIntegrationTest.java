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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O teste que a etapa 10 nao pode escrever.
 *
 * La, creditar a mesma conta de varias threads ao mesmo tempo teria falhado de
 * forma intermitente, e eu deixei escrito no codigo que era assunto para depois.
 * Depois e agora: com quatro consumidores e lock pessimista, oito creditos
 * simultaneos na mesma conta tem que somar exatamente oito creditos.
 */
class BalanceConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final int LANCAMENTOS = 8;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("oito creditos concorrentes na mesma conta somam sem perder nenhum")
    void creditosConcorrentesNaMesmaConta() throws Exception {
        Account conta = novaConta();

        for (int i = 0; i < LANCAMENTOS; i++) {
            postTransacao(conta.getId(), TransactionType.CREDIT, "10.00")
                    .andExpect(status().isAccepted());
        }

        aguardar(() -> {
            assertThat(transactionRepository.findAll())
                    .hasSize(LANCAMENTOS)
                    .allSatisfy(t -> assertThat(t.getStatus()).isEqualTo(TransactionStatus.PROCESSED));

            assertThat(saldoDe(conta.getId()))
                    .as("oito creditos de 10 sao 80; qualquer valor menor e atualizacao perdida")
                    .isEqualByComparingTo("80.00");
        });
    }

    /**
     * A recusa agora tem nome.
     *
     * Ate a etapa 12, um debito impossivel morria com "operacao viola uma
     * restricao de integridade", vindo da constraint do banco. Agora a regra e da
     * aplicacao e o motivo fica legivel no proprio lancamento.
     */
    @Test
    @DisplayName("debito acima do saldo falha com motivo de saldo insuficiente")
    void debitoAcimaDoSaldoTemMotivoExplicito() throws Exception {
        Account conta = novaConta();

        UUID credito = idDaResposta(postTransacao(conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isAccepted()));
        aguardar(() -> assertThat(statusDe(credito)).isEqualTo(TransactionStatus.PROCESSED));

        UUID debito = idDaResposta(postTransacao(conta.getId(), TransactionType.DEBIT, "500.00")
                .andExpect(status().isAccepted()));
        aguardar(() -> assertThat(statusDe(debito)).isEqualTo(TransactionStatus.FAILED));

        Transaction falha = transactionRepository.findById(debito).orElseThrow();
        assertThat(falha.getFailureReason())
                .as("a mensagem tem que explicar o que houve, nao citar constraint de banco")
                .containsIgnoringCase("saldo insuficiente");

        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("debito exatamente igual ao saldo e permitido e zera a conta")
    void debitoIgualAoSaldo() throws Exception {
        Account conta = novaConta();

        UUID credito = idDaResposta(postTransacao(conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isAccepted()));
        aguardar(() -> assertThat(statusDe(credito)).isEqualTo(TransactionStatus.PROCESSED));

        UUID debito = idDaResposta(postTransacao(conta.getId(), TransactionType.DEBIT, "100.00")
                .andExpect(status().isAccepted()));

        aguardar(() -> {
            assertThat(statusDe(debito)).isEqualTo(TransactionStatus.PROCESSED);
            assertThat(saldoDe(conta.getId())).isEqualByComparingTo("0.00");
        });
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
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(contaId, tipo, new BigDecimal(valor), "teste"))));
    }
}
