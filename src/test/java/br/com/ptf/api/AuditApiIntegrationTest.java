package br.com.ptf.api;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.domain.AuditLog;
import br.com.ptf.api.domain.TransactionStatus;
import br.com.ptf.api.domain.TransactionType;
import br.com.ptf.api.dto.CreateAccountRequest;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.AuditLogRepository;
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

class AuditApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("criar conta registra ACCOUNT_CREATED")
    void contaCriadaEAuditada() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequest("12345678901", "Vitor Camprubi"))))
                .andExpect(status().isCreated());

        assertThat(auditLogRepository.countByAction(AuditAction.ACCOUNT_CREATED)).isEqualTo(1);
    }

    /**
     * A trilha agora tem dois momentos, e essa separacao e a propria mudanca da
     * etapa 12: CREATED e gravado pela requisicao, PROCESSED pelo consumidor,
     * transacoes diferentes, instantes diferentes.
     */
    @Test
    @DisplayName("transacao gera CREATED na requisicao e PROCESSED no consumidor")
    void transacaoAuditadaNosDoisMomentos() throws Exception {
        Account conta = novaConta();

        postTransacao(null, conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isAccepted());

        assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_CREATED)).isEqualTo(1);

        aguardar(() ->
                assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_PROCESSED)).isEqualTo(1));

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(2);
        assertThat(logs).allSatisfy(log -> assertThat(log.getEntityType()).isEqualTo("Transaction"));
    }

    @Test
    @DisplayName("replay registra TRANSACTION_REPLAYED e nao duplica o CREATED")
    void replayEAuditado() throws Exception {
        Account conta = novaConta();
        String chave = UUID.randomUUID().toString();

        postTransacao(chave, conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isAccepted());
        postTransacao(chave, conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isOk());

        assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_CREATED)).isEqualTo(1);
        assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_REPLAYED)).isEqualTo(1);
    }

    /**
     * A prova do REQUIRES_NEW.
     *
     * A requisicao termina em 409 e a transacao inteira volta atras. Mas o
     * registro de que alguem reutilizou a chave com outro payload continua la,
     * porque foi gravado numa transacao separada que ja tinha commitado.
     */
    @Test
    @DisplayName("conflito de chave da rollback na operacao mas a auditoria sobrevive")
    void conflitoAuditadoSobreviveAoRollback() throws Exception {
        Account conta = novaConta();
        String chave = UUID.randomUUID().toString();

        postTransacao(chave, conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isAccepted());
        postTransacao(chave, conta.getId(), TransactionType.CREDIT, "200.00")
                .andExpect(status().isConflict());

        assertThat(transactionRepository.count()).isEqualTo(1);

        assertThat(auditLogRepository.countByAction(AuditAction.IDEMPOTENCY_CONFLICT))
                .as("a tentativa recusada tem que deixar rastro")
                .isEqualTo(1);

        assertThat(auditLogRepository.findByEntityIdOrderByCreatedAtDesc(chave))
                .singleElement()
                .satisfies(log -> assertThat(log.getEntityType()).isEqualTo("IdempotencyKey"));
    }

    /**
     * O espelho: falha no processamento gera FAILED e nunca PROCESSED.
     *
     * A auditoria de sucesso e gravada na mesma transacao que aplica o saldo, e o
     * rollback leva as duas juntas. Nao pode existir registro afirmando que um
     * debito aconteceu quando ele nao aconteceu.
     */
    @Test
    @DisplayName("debito recusado gera TRANSACTION_FAILED e nenhum PROCESSED")
    void falhaNoProcessamentoEAuditada() throws Exception {
        Account conta = novaConta();

        postTransacao(null, conta.getId(), TransactionType.DEBIT, "500.00")
                .andExpect(status().isAccepted());

        aguardar(() ->
                assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_FAILED)).isEqualTo(1));

        assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_PROCESSED)).isZero();
        assertThat(transactionRepository.findAll())
                .singleElement()
                .satisfies(t -> assertThat(t.getStatus()).isEqualTo(TransactionStatus.FAILED));
        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("0.00");
    }

    private Account novaConta() {
        return accountRepository.save(new Account("12345678901", "Vitor Camprubi"));
    }

    private BigDecimal saldoDe(UUID contaId) {
        return accountRepository.findById(contaId).orElseThrow().getBalance();
    }

    private ResultActions postTransacao(String chave, UUID contaId, TransactionType tipo, String valor)
            throws Exception {
        var requisicao = post("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(
                        contaId, tipo, new BigDecimal(valor), "teste")));

        if (chave != null) {
            requisicao = requisicao.header("Idempotency-Key", chave);
        }

        return mockMvc.perform(requisicao);
    }
}
