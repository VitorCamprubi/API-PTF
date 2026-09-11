package br.com.ptf.api;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.domain.AuditLog;
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

    @Test
    @DisplayName("criar transacao registra TRANSACTION_CREATED com tipo e valor")
    void transacaoCriadaEAuditada() throws Exception {
        Account conta = novaConta();

        postTransacao(null, conta.getId(), "100.00").andExpect(status().isCreated());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(AuditAction.TRANSACTION_CREATED);
        assertThat(logs.get(0).getEntityType()).isEqualTo("Transaction");
        assertThat(logs.get(0).getDetail()).contains("CREDIT").contains("100.0000");
    }

    @Test
    @DisplayName("replay registra TRANSACTION_REPLAYED e nao duplica o CREATED")
    void replayEAuditado() throws Exception {
        Account conta = novaConta();
        String chave = UUID.randomUUID().toString();

        postTransacao(chave, conta.getId(), "100.00").andExpect(status().isCreated());
        postTransacao(chave, conta.getId(), "100.00").andExpect(status().isOk());

        assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_CREATED)).isEqualTo(1);
        assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_REPLAYED)).isEqualTo(1);
    }

    /**
     * A prova do REQUIRES_NEW.
     *
     * A requisicao termina em 409 e a transacao inteira volta atras: nenhum
     * lancamento novo, saldo intacto. Mas o registro de que alguem reutilizou a
     * chave com outro payload continua la, porque foi gravado numa transacao
     * separada que ja tinha commitado.
     */
    @Test
    @DisplayName("conflito de chave da rollback na operacao mas a auditoria sobrevive")
    void conflitoAuditadoSobreviveAoRollback() throws Exception {
        Account conta = novaConta();
        String chave = UUID.randomUUID().toString();

        postTransacao(chave, conta.getId(), "100.00").andExpect(status().isCreated());
        postTransacao(chave, conta.getId(), "200.00").andExpect(status().isConflict());

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("100.00");

        assertThat(auditLogRepository.countByAction(AuditAction.IDEMPOTENCY_CONFLICT))
                .as("a tentativa recusada tem que deixar rastro")
                .isEqualTo(1);

        assertThat(auditLogRepository.findByEntityIdOrderByCreatedAtDesc(chave))
                .singleElement()
                .satisfies(log -> assertThat(log.getEntityType()).isEqualTo("IdempotencyKey"));
    }

    /**
     * O espelho do teste anterior.
     *
     * Aqui a auditoria foi gravada na mesma transacao do lancamento, entao o
     * rollback causado pela constraint de saldo leva as duas coisas embora. E o
     * comportamento desejado: nao pode existir registro afirmando que um debito
     * aconteceu quando ele nao aconteceu.
     */
    @Test
    @DisplayName("debito recusado pelo banco nao deixa auditoria de transacao criada")
    void rollbackApagaAuditoriaDaMesmaTransacao() throws Exception {
        Account conta = novaConta();

        postTransacao(null, conta.getId(), "100.00").andExpect(status().isCreated());
        postTransacaoDebito(conta.getId(), "500.00").andExpect(status().isConflict());

        assertThat(auditLogRepository.countByAction(AuditAction.TRANSACTION_CREATED))
                .as("so o credito bem-sucedido pode ter deixado registro")
                .isEqualTo(1);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    private Account novaConta() {
        return accountRepository.save(new Account("12345678901", "Vitor Camprubi"));
    }

    private BigDecimal saldoDe(UUID contaId) {
        return accountRepository.findById(contaId).orElseThrow().getBalance();
    }

    private ResultActions postTransacao(String chave, UUID contaId, String valor) throws Exception {
        var requisicao = post("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(
                        contaId, TransactionType.CREDIT, new BigDecimal(valor), "deposito")));

        if (chave != null) {
            requisicao = requisicao.header("Idempotency-Key", chave);
        }

        return mockMvc.perform(requisicao);
    }

    private ResultActions postTransacaoDebito(UUID contaId, String valor) throws Exception {
        return mockMvc.perform(post("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(
                        contaId, TransactionType.DEBIT, new BigDecimal(valor), "saque"))));
    }
}
