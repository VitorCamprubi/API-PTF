package br.com.ptf.api;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.TransactionType;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.TransactionRepository;
import br.com.ptf.api.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste que a etapa 9 nao tinha como escrever.
 *
 * Ele ataca o service direto, sem passar por HTTP, porque a corrida esta no
 * service e nao no controller. Oito threads sao soltas ao mesmo tempo por um
 * CountDownLatch: sem a largada sincronizada elas se enfileirariam naturalmente e
 * o teste passaria mesmo com o codigo errado, que e o pior tipo de teste.
 */
class ConcurrencyIntegrationTest extends IntegrationTestSupport {

    private static final int THREADS = 8;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("oito requisicoes simultaneas com a mesma chave produzem um unico lancamento")
    void mesmaChaveEmParalelo() throws Exception {
        Account conta = accountRepository.save(new Account("12345678901", "Vitor Camprubi"));
        String chave = UUID.randomUUID().toString();

        CreateTransactionRequest pedido = new CreateTransactionRequest(
                conta.getId(), TransactionType.CREDIT, new BigDecimal("100.00"), "deposito");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<TransactionService.Result>> futuros = new ArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return transactionService.create(chave, pedido);
            }));
        }

        largada.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        List<TransactionService.Result> resultados = new ArrayList<>();
        for (Future<TransactionService.Result> futuro : futuros) {
            // get() relanca qualquer excecao que tenha estourado na thread.
            // Nenhuma das oito pode ter falhado: as sete perdedoras devem receber
            // replay, e nao erro de chave duplicada.
            resultados.add(futuro.get());
        }

        long criadas = resultados.stream().filter(resultado -> !resultado.replay()).count();
        long replays = resultados.stream().filter(TransactionService.Result::replay).count();

        assertThat(criadas).isEqualTo(1);
        assertThat(replays).isEqualTo(THREADS - 1);

        assertThat(resultados.stream()
                .map(resultado -> resultado.transaction().getId())
                .distinct()
                .count())
                .as("todas as threads tem que receber a mesma transacao")
                .isEqualTo(1);

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(accountRepository.findById(conta.getId()).orElseThrow().getBalance())
                .as("o saldo tem que refletir um credito, nao oito")
                .isEqualByComparingTo("100.00");
    }

    /**
     * Uma conta por thread, de proposito.
     *
     * Se as oito creditassem a mesma conta, o teste falharia de forma
     * intermitente por perda de atualizacao no saldo: cada transacao le o saldo,
     * soma em memoria e grava o total que calculou, entao a ultima a gravar
     * sobrescreve o trabalho das anteriores. Esse e um problema real e ainda
     * aberto no projeto, mas e outro problema, e a etapa 13 resolve ele com lock
     * pessimista. Misturar os dois aqui esconderia qual dos dois quebrou.
     */
    @Test
    @DisplayName("chaves diferentes em paralelo nao se bloqueiam e geram um lancamento cada")
    void chavesDiferentesEmParalelo() throws Exception {
        List<Account> contas = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            contas.add(accountRepository.save(new Account("1234567890" + i, "Titular " + i)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<TransactionService.Result>> futuros = new ArrayList<>();

        for (Account conta : contas) {
            String chavePropria = UUID.randomUUID().toString();
            CreateTransactionRequest pedido = new CreateTransactionRequest(
                    conta.getId(), TransactionType.CREDIT, new BigDecimal("10.00"), "deposito");

            futuros.add(pool.submit(() -> {
                largada.await();
                return transactionService.create(chavePropria, pedido);
            }));
        }

        largada.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        for (Future<TransactionService.Result> futuro : futuros) {
            assertThat(futuro.get().replay()).isFalse();
        }

        assertThat(transactionRepository.count()).isEqualTo(THREADS);
        for (Account conta : contas) {
            assertThat(accountRepository.findById(conta.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("10.00");
        }
    }
}
