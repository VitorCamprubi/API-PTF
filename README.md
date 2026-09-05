# API PTF - Financial Transactions

API backend Java/Spring Boot para processamento de transacoes financeiras.

Projeto em construcao. Este README sera evoluido a cada etapa.

## Stack atual

- Java 21
- Spring Boot 3.4.5
- Spring Web
- Spring Actuator
- Spring Data JPA
- PostgreSQL 16
- Flyway (versionamento de schema)

## Como rodar

Suba o banco primeiro (precisa de Docker):

```bash
docker compose up -d
```

Depois a aplicacao:

```bash
mvn spring-boot:run
```

A aplicacao sobe na porta 8081 (definida em `application.yml`).
O Postgres do container sobe na porta 5433 do host (a 5432 pode estar
ocupada por um Postgres instalado localmente), com banco/usuario/senha `ptf`.

Health check (inclui o status da conexao com o banco):

```bash
curl http://localhost:8081/actuator/health
```

Para derrubar o banco:

```bash
docker compose down
```

Para derrubar e apagar os dados (recomeca do zero, roda as migrations de novo):

```bash
docker compose down -v
```

## Banco de dados

O schema e versionado com Flyway. As migrations ficam em
`src/main/resources/db/migration` e rodam automaticamente no startup da
aplicacao, em ordem de versao.

O Hibernate esta em `ddl-auto: validate`: ele nunca altera o schema,
apenas confere se as entidades batem com o que o Flyway criou.
