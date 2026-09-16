package br.com.ptf.api;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.TransactionStatus;
import br.com.ptf.api.domain.TransactionType;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Observabilidade tambem quebra, e quando quebra ninguem percebe.
 *
 * Um endpoint de metricas que parou de expor o que importa nao derruba requisicao
 * nenhuma: ele so faz o alarme nunca disparar no dia do incidente. Por isso estes
 * testes existem, e por isso eles verificam nomes de metrica especificos e nao
 * apenas "o endpoint respondeu 200".
 */
class ObservabilityIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("as metricas de negocio aparecem no endpoint do prometheus")
    void metricasDeNegocioExpostas() throws Exception {
        Account conta = accountRepository.save(new Account("12345678901", "Vitor Camprubi"));

        UUID credito = idDoCredito(conta.getId());
        aguardar(() -> assertThat(transactionRepository.findById(credito).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.PROCESSED));

        String corpo = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(corpo)
                .contains("ptf_transactions_accepted_total")
                .contains("ptf_transactions_processed_total")
                .contains("ptf_outbox_pending");

        assertThat(corpo)
                .as("toda metrica tem que sair carimbada com a aplicacao")
                .contains("application=\"api-ptf\"");

        assertThat(corpo)
                .as("o histograma tem que estar ligado, senao nao existe p95 nem p99")
                .contains("ptf_transactions_processing_seconds_bucket");

        assertThat(corpo)
                .as("nome de metrica com sufixo reservado do protocolo e renomeado em silencio")
                .doesNotContain("ptf_transactions_total{");
    }

    @Test
    @DisplayName("a especificacao OpenAPI e gerada e descreve os endpoints reais")
    void openApiGerado() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API PTF"))
                .andExpect(jsonPath("$.paths./accounts").exists())
                .andExpect(jsonPath("$.paths./transactions").exists())
                .andExpect(jsonPath("$.paths./accounts/{id}/balance").exists())
                .andExpect(jsonPath("$.paths./transactions.post.responses.202").exists());
    }

    @Test
    @DisplayName("o health check reporta banco e broker")
    void healthComDetalhes() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.rabbit.status").value("UP"));
    }

    private UUID idDoCredito(UUID contaId) throws Exception {
        String corpo = mockMvc.perform(post("/transactions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateTransactionRequest(
                                contaId, TransactionType.CREDIT, new BigDecimal("100.00"), "teste"))))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(corpo).get("id").asText());
    }
}
