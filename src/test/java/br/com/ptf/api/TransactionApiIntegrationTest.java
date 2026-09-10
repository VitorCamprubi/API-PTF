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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("credito seguido de debito atualiza o saldo")
    void creditoEDebito() throws Exception {
        Account conta = novaConta();

        postTransacao(conta.getId(), TransactionType.CREDIT, "1000.50")
                .andExpect(status().isCreated());

        postTransacao(conta.getId(), TransactionType.DEBIT, "300.25")
                .andExpect(status().isCreated());

        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("700.25");
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    /**
     * O teste que justifica a etapa inteira.
     *
     * A aplicacao ainda nao verifica saldo suficiente: quem recusa e a constraint
     * chk_accounts_balance_non_negative, no commit. O que importa aqui nao e so o
     * status 409, e o estado depois dele: o INSERT da transacao chegou a ser
     * enviado ao banco antes do UPDATE falhar, e o rollback tem que ter desfeito
     * os dois. Saldo intacto e nenhum lancamento orfao.
     */
    @Test
    @DisplayName("debito maior que o saldo devolve 409 e nao deixa rastro")
    void debitoAcimaDoSaldo() throws Exception {
        Account conta = novaConta();

        postTransacao(conta.getId(), TransactionType.CREDIT, "100.00")
                .andExpect(status().isCreated());

        postTransacao(conta.getId(), TransactionType.DEBIT, "500.00")
                .andExpect(status().isConflict());

        assertThat(saldoDe(conta.getId())).isEqualByComparingTo("100.00");
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("transacao em conta inexistente devolve 404 e nao cria lancamento")
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

    private org.springframework.test.web.servlet.ResultActions postTransacao(UUID contaId,
                                                                             TransactionType tipo,
                                                                             String valor) throws Exception {
        return mockMvc.perform(post("/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateTransactionRequest(contaId, tipo, new BigDecimal(valor), "teste"))));
    }
}
