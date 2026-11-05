# CLAUDE.md

Este arquivo orienta o Claude Code (claude.ai/code) ao trabalhar com o código deste repositório.

## Idioma

**Sempre responda ao usuário em português e escreva todos os comentários de código em português** (no estilo do projeto, sem acentos).

## Comandos

```bash
docker compose up -d                       # Postgres (localhost:5433) + RabbitMQ (5672, painel 15672), credenciais ptf/ptf
mvn spring-boot:run                        # API em http://localhost:8081 (Swagger: /swagger-ui.html)
docker compose --profile full up -d --build  # tudo em container, inclusive a API

mvn clean test                             # suite completa
mvn test -Dtest=OutboxIntegrationTest      # uma classe de teste
mvn test -Dtest=ConcurrencyIntegrationTest#nomeDoMetodo   # um método de teste
```

Todos os testes são de integração. Eles precisam do **Docker rodando**, porque o Testcontainers sobe um Postgres 16 e um RabbitMQ 3.13 próprios. Os testes não usam o compose. Não há linter configurado.

## Arquitetura

A stack é Java 21 e Spring Boot 3.4, com Postgres, Flyway, RabbitMQ, Micrometer/Prometheus e springdoc. Pacote raiz: `br.com.ptf.api`.

Os lançamentos são **aceitos de forma síncrona e aplicados de forma assíncrona**:

1. `TransactionService.create` (lado da requisição) valida o pedido e verifica se a conta existe. Se não existir, devolve 404 na hora. Depois, em **uma única transação de banco**, insere a `Transaction` como `PENDING`, o registro de idempotência, o log de auditoria e uma linha em `outbox_messages`. Por fim, devolve `202`. Esse service nunca mexe no saldo.
2. `OutboxScheduler`/`OutboxPublisher` leem a outbox a cada `ptf.outbox.intervalo-ms` (500 ms) com `FOR UPDATE SKIP LOCKED` e publicam no RabbitMQ. A entrega é **pelo menos uma vez**.
3. `TransactionListener` roda com 4 a 8 consumidores e chama `TransactionProcessingService.process`. Esse método:
   - busca o id da conta;
   - **trava a conta (`findByIdForUpdate`) antes de carregar as entidades** de transação e conta;
   - ignora a transação se ela não estiver mais `PENDING`, o que garante a idempotência do lado do consumidor;
   - chama `transaction.apply()` e `markProcessed()`.

   Mantenha essa ordem, primeiro travar e depois carregar. Carregar antes de travar traz de volta o *lost update*.
4. A política de erro do listener depende do tipo da exceção:
   - `BusinessException`, como saldo insuficiente, é falha permanente. `markFailed` roda em transação própria e a mensagem é confirmada.
   - Qualquer outra exceção é relançada. O Spring AMQP tenta de novo 3 vezes, com espera crescente (ver `application.yml`). Se ainda falhar, a mensagem vai para a **dead letter queue**. A DLQ não tem consumidor, de propósito.

A idempotência do lado da requisição usa o header `Idempotency-Key` e um hash SHA-256 do payload canônico, guardado em `idempotency_keys`:
- As requisições concorrentes são serializadas com `AdvisoryLockRepository` (`pg_advisory_xact_lock`), porque ainda não existe linha para travar.
- Um replay devolve `200` com `Idempotent-Replay: true`.
- A mesma chave com outro payload devolve `409`, e a tentativa é auditada.

## Convenções e invariantes

- **O schema pertence ao Flyway** (`src/main/resources/db/migration`), e o Hibernate roda com `ddl-auto: validate`. Nunca edite uma migration já aplicada; crie uma nova, `V7__...`. Para uma coluna `NOT NULL` nova, faça o backfill antes (ver a V5).
- **Dinheiro:** use `BigDecimal` / `NUMERIC(19,4)` (`Account.MONETARY_SCALE`), nunca `double`.
- **Domínio rico:** `Account` não tem `setBalance`; o saldo muda por `credit`/`debit`. Os campos financeiros de `Transaction` são imutáveis, e só o status de processamento muda. Correção se faz com estorno, nunca com UPDATE.
- **IDs:** os UUIDs são gerados na aplicação, então o id já existe antes do INSERT.
- **Exceções:** as exceções de domínio não conhecem HTTP. Só o `GlobalExceptionHandler` as traduz para status code, pelas famílias `NotFoundException`/`ConflictException`/`BusinessException`. Estenda uma família em vez de criar um handler novo.
- **Auditoria:** `AuditService.record` usa propagação `MANDATORY`, então o registro volta atrás junto com a operação. `recordInNewTransaction` usa `REQUIRES_NEW` e serve para tentativas recusadas, que precisam sobreviver ao rollback. `audit_logs` não tem chaves estrangeiras, de propósito.
- **Métricas:** as tags só aceitam valores de um conjunto fechado. Nunca use id ou mensagem de erro como tag.
- **Estilo de código:** sem Lombok, injeção por construtor, DTOs como `record`. Comentários e muitos identificadores estão em português sem acentos (por exemplo `registrar`, `aguardar`, `contarFalha`); siga esse padrão.

## Testes

- Todo teste estende `IntegrationTestSupport`. Essa classe sobe os containers como singleton, limpa as tabelas antes de cada teste e oferece `aguardar(...)`, uma espera com Awaitility de até 20 s, para verificar resultados assíncronos. Não use `Thread.sleep`.
- **Não coloque anotações de teste do Spring (`@MockBean`, `@TestPropertySource` etc.) nas subclasses.** Elas mudam a chave de cache do contexto e criam um segundo `ApplicationContext`. Os dois contextos disputam a mesma fila do RabbitMQ e os testes ficam intermitentes. Configuração compartilhada fica na classe base.
- Se criar uma tabela nova, inclua-a no `TRUNCATE` de `IntegrationTestSupport.limparBase`, a menos que um cascade de chave estrangeira já a cubra.
