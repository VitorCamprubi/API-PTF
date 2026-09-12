package br.com.ptf.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base dos testes de integracao.
 *
 * Agora sao dois containers: Postgres e RabbitMQ. O broker nao e simulado nem
 * substituido por um mock, porque metade dos problemas de mensageria mora na
 * serializacao da mensagem, no binding entre exchange e fila e no ack, e nenhum
 * mock reproduz isso.
 *
 * Os dois seguem o padrao singleton pelo mesmo motivo de antes: o Spring cacheia
 * o ApplicationContext entre as classes de teste, e um container que morresse ao
 * fim de cada classe voltaria numa porta nova, deixando o contexto cacheado
 * apontando para o vazio.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(20);

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ptf")
            .withUsername("ptf")
            .withPassword("ptf");

    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management"));

    static {
        POSTGRES.start();
        RABBITMQ.start();
    }

    @DynamicPropertySource
    static void propriedadesDeInfra(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
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
     */
    @BeforeEach
    void limparBase() {
        jdbcTemplate.execute("TRUNCATE TABLE accounts, transactions, audit_logs CASCADE");
    }

    /**
     * Espera ativa com prazo, para o que agora acontece depois da resposta HTTP.
     *
     * A alternativa preguicosa seria Thread.sleep(2000) e torcer. Isso deixa o
     * teste lento quando o processamento e rapido e intermitente quando a maquina
     * esta ocupada, que sao as duas piores propriedades que um teste pode ter.
     * Aqui a condicao e verificada a cada 100ms e o teste segue assim que ela
     * passa, falhando so se nao acontecer dentro do prazo.
     */
    protected void aguardar(ThrowingRunnable verificacao) {
        Awaitility.await()
                .atMost(ESPERA_MAXIMA)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(verificacao);
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
