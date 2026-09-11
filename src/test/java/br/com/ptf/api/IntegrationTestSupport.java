package br.com.ptf.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integracao.
 *
 * Sobe um Postgres de verdade em container e aponta a aplicacao para ele. Nao e
 * banco em memoria: H2 aceita SQL que o Postgres recusa, nao tem TIMESTAMPTZ,
 * trata NUMERIC de outro jeito e nao roda as migrations do Flyway do mesmo modo.
 * Um teste que passa no H2 e quebra em producao nao e teste, e falsa seguranca.
 *
 * O container e um singleton iniciado no bloco static e nunca parado. O motivo e
 * concreto: o Spring cacheia o ApplicationContext entre classes de teste que
 * compartilham a mesma configuracao. Se o container morresse no fim de cada
 * classe e subisse de novo numa porta aleatoria diferente, a segunda classe
 * reusaria um contexto cacheado com um DataSource apontando para uma porta morta.
 * Quem derruba o container no fim da suite e o Ryuk, container auxiliar que o
 * proprio Testcontainers sobe para limpar tudo quando a JVM termina.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ptf")
            .withUsername("ptf")
            .withPassword("ptf");

    static {
        POSTGRES.start();
    }

    /**
     * A porta do container e sorteada a cada execucao, entao a URL do datasource
     * so existe em tempo de execucao. DynamicPropertySource injeta o valor depois
     * que o container ja subiu e antes do contexto do Spring ser criado.
     */
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Cada teste comeca com o banco limpo.
     *
     * TRUNCATE em vez de deleteAll(): nao carrega entidade nenhuma para memoria e
     * nao dispara callback de JPA. O CASCADE limpa tambem qualquer tabela que
     * referencie estas por chave estrangeira, o que resolve idempotency_keys
     * sozinho. audit_logs precisou entrar na lista na mao: ela nao tem chave
     * estrangeira para ninguem, de proposito, para que apagar um registro de
     * origem nunca apague a trilha de auditoria dele.
     *
     * A alternativa comum seria anotar a classe de teste com @Transactional e
     * deixar o rollback limpar. Nao serve aqui: o teste passaria a rodar dentro da
     * mesma transacao do codigo testado, e e justamente commit, rollback e
     * constraint disparando no flush que a gente quer observar.
     */
    @BeforeEach
    void limparBase() {
        jdbcTemplate.execute("TRUNCATE TABLE accounts, transactions, audit_logs CASCADE");
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
