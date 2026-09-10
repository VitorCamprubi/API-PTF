package br.com.ptf.api;

import br.com.ptf.api.domain.Account;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotencyApiIntegrationTest extends IntegrationTestSupport {

    private static final String CHAVE = "Idempotency-Key";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("mesma chave e mesmo payload: a segunda chamada devolve a transacao original sem debitar de novo")
    void replay() throws Exception {
        Account conta = novaConta();
        String chave = UUID.randomUUID().toString();

        String primeiroId = idDaResposta(
                postTransacao(chave, conta.getId(), "100.00")
                        .andExpect(status().isCreated()));

        String segundoId = idDaResposta(
                postTransacao(chave, conta.getId(), "100.00")
                        .andExpect(status().isOk())
                        .andExpect(header().string("Idempotent-Replay", "true")));

        assertThat(segundoId).isEqualTo(primeiroId);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("mesma chave com payload diferente devolve 409")
    void chaveReutilizadaComOutroPayload() throws Exception {
        Account conta = novaConta();
        String chave = UUID.randomUUID().toString();

        postTransacao(chave, conta.getId(), "100.00")
                .andExpect(status().isCreated());

        postTransacao(chave, conta.getId(), "200.00")
                .andExpect(status().isConflict());

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("chaves diferentes com o mesmo payload criam duas transacoes")
    void chavesDiferentes() throws Exception {
        Account conta = novaConta();

        postTransacao(UUID.randomUUID().toString(), conta.getId(), "100.00")
                .andExpect(status().isCreated());

        postTransacao(UUID.randomUUID().toString(), conta.getId(), "100.00")
                .andExpect(status().isCreated());

        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("200.00");
    }

    /**
     * Documenta o limite atual do desenho, e nao um comportamento desejavel: sem
     * a chave, dois envios identicos sao dois lancamentos, porque a API nao tem
     * como saber que o segundo era retry do primeiro.
     */
    @Test
    @DisplayName("sem chave de idempotencia, duas chamadas iguais criam duas transacoes")
    void semChave() throws Exception {
        Account conta = novaConta();

        postTransacao(null, conta.getId(), "100.00").andExpect(status().isCreated());
        postTransacao(null, conta.getId(), "100.00").andExpect(status().isCreated());

        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("200.00");
    }

    private Account novaConta() {
        return accountRepository.save(new Account("12345678901", "Vitor Camprubi"));
    }

    private BigDecimal saldoDe(UUID contaId) {
        return accountRepository.findById(contaId).orElseThrow().getBalance();
    }

    private String idDaResposta(ResultActions resultado) throws Exception {
        String corpo = resultado.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("id").asText();
    }

    private ResultActions postTransacao(String chave, UUID contaId, String valor) throws Exception {
        var requisicao = post("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(
                        contaId, TransactionType.CREDIT, new BigDecimal(valor), "deposito")));

        if (chave != null) {
            requisicao = requisicao.header(CHAVE, chave);
        }

        return mockMvc.perform(requisicao);
    }
}
