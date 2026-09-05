# API PTF - Financial Transactions

API backend Java/Spring Boot para processamento de transacoes financeiras.

Projeto em construcao. Este README sera evoluido a cada etapa.

## Stack atual

- Java 21
- Spring Boot 3.4.5
- Spring Web
- Spring Actuator

## Como rodar

```bash
mvn spring-boot:run
```

A aplicacao sobe na porta 8081 (definida em `application.yml`).

Health check:

```bash
curl http://localhost:8081/actuator/health
```
