# API PTF

API de processamento de transacoes financeiras, escrita em Java 21 com Spring Boot.

O lancamento e **aceito de forma sincrona e aplicado de forma assincrona**: a
requisicao valida o pedido, grava a transacao como `PENDING` e responde `202`.
Um consumidor pega a mensagem na fila, trava a conta, aplica o valor e marca
`PROCESSED` ou `FAILED`. Entre os dois lados existe um *transactional outbox*,
para que nenhuma transacao aceita deixe de ser processada por causa de uma queda
de processo.

O projeto foi construido em etapas, cada uma resolvendo um problema concreto do
anterior. As decisoes estao registradas abaixo, com o motivo.

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4 (Web, Data JPA, Validation, AMQP, Actuator) |
| Banco | PostgreSQL 16 |
| Migrations | Flyway |
| Mensageria | RabbitMQ 3.13 |
| Metricas | Micrometer + Prometheus |
| Documentacao | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, AssertJ, Testcontainers, Awaitility |
| Build | Maven |

Sem Lombok, de proposito: o codigo mostra o que faz.

---

## Como o dinheiro se move

```
POST /transactions
        |
        v
  [ validacao ]  conta existe? valor positivo? chave de idempotencia ja usada?
        |
        v
  ---------------------- uma transacao de banco ----------------------
  | INSERT transactions (status = PENDING)                            |
  | INSERT idempotency_keys                                           |
  | INSERT audit_logs      (TRANSACTION_CREATED)                      |
  | INSERT outbox_messages (a ordem de publicar)                      |
  --------------------------------------------------------------------
        |
        v
   202 Accepted  ------>  cliente acompanha em GET /transactions/{id}
        |
        v
  [ OutboxScheduler ]  a cada 500ms: SELECT ... FOR UPDATE SKIP LOCKED
        |
        v
   RabbitMQ  (exchange ptf.transactions -> fila ptf.transactions.process)
        |
        v
  [ TransactionListener ]  4 consumidores
        |
        v
  ---------------------- uma transacao de banco ----------------------
  | SELECT ... FOR UPDATE na conta   <- serializa o saldo             |
  | regra de saldo insuficiente                                       |
  | UPDATE accounts   (dirty checking)                                |
  | UPDATE transactions (status = PROCESSED)                          |
  | INSERT audit_logs   (TRANSACTION_PROCESSED)                       |
  --------------------------------------------------------------------

  falha de negocio    -> FAILED com motivo, mensagem confirmada
  falha transitoria   -> 3 tentativas com espera crescente -> dead letter queue
```

---

## Rodando

Precisa de Docker e, para o modo de desenvolvimento, Java 21 e Maven.

**Tudo em container, um comando:**

```bash
docker compose --profile full up -d --build
```

**Desenvolvimento (infra em container, aplicacao na maquina):**

```bash
docker compose up -d          # postgres + rabbitmq
mvn spring-boot:run
```

**Testes** (sobem os proprios containers via Testcontainers; nao dependem do
compose, mas exigem Docker rodando):

```bash
mvn clean test
```

| Servico | Endereco |
|---|---|
| API | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Health | http://localhost:8081/actuator/health |
| Metricas Prometheus | http://localhost:8081/actuator/prometheus |
| Painel do RabbitMQ | http://localhost:15672 (ptf / ptf) |
| Postgres | localhost:5433 (ptf / ptf / ptf) |

---

## Endpoints

| Metodo | Rota | O que faz |
|---|---|---|
| POST | `/accounts` | Cria conta. `201` + `Location`. Documento unico. |
| GET | `/accounts/{id}` | Dados cadastrais da conta. |
| GET | `/accounts/{id}/balance` | Saldo atual. Nunca cacheavel. |
| POST | `/transactions` | Registra lancamento. `202` + `Location`. Aceita `Idempotency-Key`. |
| GET | `/transactions/{id}` | Status do lancamento: `PENDING`, `PROCESSED` ou `FAILED`. |

Exemplo:

```bash
# cria a conta
curl -i -X POST http://localhost:8081/accounts \
  -H "Content-Type: application/json" \
  -d '{"document":"12345678901","holderName":"Vitor Camprubi"}'

# credita 1000.50 (troque o accountId pelo id devolvido acima)
curl -i -X POST http://localhost:8081/transactions \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 8b1f0e9c-2a3d-4f55-9c31-7e2b6d40a111" \
  -d '{"accountId":"<id>","type":"CREDIT","amount":1000.50,"description":"deposito"}'

# consulta o saldo
curl -s http://localhost:8081/accounts/<id>/balance
```

Reenviar o segundo comando com a **mesma** `Idempotency-Key` devolve `200` com o
lancamento original e o header `Idempotent-Replay: true`, sem creditar de novo.
A mesma chave com valor diferente devolve `409`.

---

## Decisoes de projeto

**Dinheiro em `NUMERIC(19,4)` e `BigDecimal`, nunca `double`.** Ponto flutuante
binario nao representa 0,1 exatamente; num sistema que soma milhoes de vezes, o
erro aparece na conciliacao e nao no teste.

**Flyway e o unico dono do schema; Hibernate em `ddl-auto: validate`.** Migration
aplicada e imutavel: mudanca entra em arquivo novo. A V5 mostra o cuidado que
isso exige — adicionar uma coluna `NOT NULL` numa tabela com dados obriga a
decidir o que fazer com o passado, e por isso ela faz o backfill antes do
`SET NOT NULL`.

**Id UUID gerado na aplicacao.** O id existe antes do INSERT, o que e o que
permite gravar a chave de idempotencia e a linha da outbox apontando para a
transacao na mesma transacao que a cria.

**Entidade com modelo rico.** `Account` nao tem `setBalance`: saldo muda por
`credit` e `debit`, que tem regra. `Transaction` e imutavel nos fatos financeiros
(conta, tipo, valor) e mutavel apenas no estado de processamento — corrigir um
lancamento errado exige estorno, nunca UPDATE.

**Excecoes de dominio nao conhecem HTTP.** A traducao para status code vive so no
`@RestControllerAdvice`. As familias `NotFoundException` e `ConflictException`
existem para que entidade nova nao exija handler novo — e, na etapa do retry,
foram elas que permitiram separar falha permanente de falha transitoria com dois
`catch`.

**Idempotencia por chave + hash do payload.** A chave vai no header porque e
metadado de protocolo, nao dado da transacao. Guardar o hash impede que a mesma
chave seja reutilizada para uma operacao diferente — a diferenca entre
idempotencia e "ignorar repetido".

**`pg_advisory_xact_lock` para a idempotencia, `SELECT ... FOR UPDATE` para o
saldo.** Sao ferramentas para problemas diferentes: na primeira requisicao de uma
chave nao existe linha para travar, e advisory lock trava um identificador; no
saldo a linha existe, e travar a linha e mais direto. A variante `_xact_` se
solta sozinha no commit, o que evita trava vazada em pool de conexoes.

**A ordem do lock importa.** No processamento, a conta e travada **antes** de ser
carregada. Travar depois de ler deixaria o saldo antigo no contexto de
persistencia e o lock chegaria tarde demais para impedir a perda de atualizacao.

**Transactional outbox.** Gravar no banco e publicar no broker sao dois sistemas
sem transacao comum. Em vez de escrever nos dois, grava-se a intencao de publicar
na mesma transacao do dado; um publicador agendado a cumpre. Isso garante **pelo
menos uma vez**, nunca exatamente uma vez — e por isso o consumidor e idempotente.

**Retry so para o que retry resolve.** Saldo insuficiente nao melhora na segunda
tentativa: vira `FAILED` com motivo. Banco indisponivel melhora: tres tentativas
com espera crescente e, se persistir, dead letter queue. A DLQ nao tem consumidor
de proposito — ela e deposito para inspecao, e reprocessar automaticamente so
transforma um problema visivel num problema invisivel.

**Auditoria com duas propagacoes.** Operacao bem-sucedida e auditada na mesma
transacao (`MANDATORY`): se o lancamento volta atras, o registro volta junto.
Tentativa recusada e auditada em transacao propria (`REQUIRES_NEW`): o rollback
nao pode apagar o fato de que alguem tentou.

**Metricas com cardinalidade controlada.** Tags so com valores de conjunto
fechado. Id de transacao ou mensagem de erro como tag geraria milhoes de series
temporais e derrubaria o servidor de metricas. Isso e assunto de log.

**Testes contra Postgres e RabbitMQ reais, via Testcontainers.** H2 aceita SQL que
o Postgres recusa e nao reproduz constraints nem `TIMESTAMPTZ`; mock de broker nao
reproduz serializacao, binding nem ack. Teste que passa contra uma imitacao e
falsa seguranca.

---

## Testes

28 testes de integracao cobrindo o caminho completo, incluindo os que so fazem
sentido num sistema concorrente:

- oito requisicoes simultaneas com a mesma chave de idempotencia produzem **um**
  lancamento e sete replays;
- oito creditos concorrentes na mesma conta somam **todos**, sem perda de
  atualizacao;
- debito acima do saldo termina em `FAILED` com motivo legivel, sem tocar no saldo;
- mensagem ilegivel vai para a dead letter queue e o consumidor continua trabalhando;
- conflito de idempotencia da rollback na operacao e **mantem** o registro de
  auditoria da tentativa.

---

## Limitacoes conhecidas

Coisas que faltam, com consciencia de que faltam:

- **Sem autenticacao.** Qualquer um cria conta e movimenta saldo. Uma API
  financeira real precisa de OAuth2/JWT e autorizacao por conta.
- **Sem transferencia entre contas.** Envolveria travar duas contas na mesma
  transacao, e travar em ordem inconsistente causa deadlock — a solucao e ordenar
  os locks por id.
- **Chaves de idempotencia nunca expiram.** APIs reais descartam depois de 24h; a
  tabela aqui cresce para sempre.
- **Outbox sem limpeza.** Linhas publicadas ficam guardadas indefinidamente.
- **Sem CI.** Nenhum pipeline roda a suite a cada push.
- **Sem paginacao nem extrato.** Nao existe listagem de lancamentos por conta.

---

## Estrutura

```
src/main/java/br/com/ptf/api/
├── config/       topologia do RabbitMQ, metricas, OpenAPI, agendamento
├── controller/   camada HTTP; nao conhece regra de negocio
├── domain/       entidades com comportamento e enums
├── dto/          contratos de entrada e saida, como records
├── exception/    excecoes de dominio e o tratamento global
├── messaging/    publicacao e consumo de mensagens
├── metrics/      metricas de negocio
├── repository/   acesso a dados, projecoes e locks
└── service/      orquestracao e transacoes

src/main/resources/db/migration/   V1..V6
src/test/java/br/com/ptf/api/      testes de integracao
```

---

Desenvolvido por [Vitor Camprubi](https://github.com/VitorCamprubi).
