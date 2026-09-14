package br.com.ptf.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma mensagem que precisa sair, guardada no mesmo banco que o dado de negocio.
 *
 * A razao de existir desta tabela cabe numa frase: gravar no Postgres e publicar
 * no RabbitMQ sao dois sistemas diferentes, e nao existe transacao que abranja os
 * dois. Qualquer ordem que voce escolha tem uma janela de falha. Publicar antes de
 * commitar pode anunciar algo que vai dar rollback; commitar antes de publicar
 * pode perder o anuncio se o processo morrer no meio. Isso se chama dual write.
 *
 * A saida e nao escrever em dois lugares. Grava-se a intencao de publicar na mesma
 * transacao do dado, no mesmo banco, com a mesma garantia de tudo ou nada. Depois,
 * um processo separado le essa intencao e a cumpre. Se ele falhar, a linha
 * continua la esperando.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Que tipo de coisa gerou isto. Serve para diagnostico e para roteamento futuro. */
    @Column(name = "aggregate_type", nullable = false, length = 50, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100, updatable = false)
    private String aggregateId;

    @Column(name = "routing_key", nullable = false, length = 100, updatable = false)
    private String routingKey;

    /** O corpo da mensagem, ja serializado em JSON. */
    @Column(name = "payload", nullable = false, length = 2000, updatable = false)
    private String payload;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected OutboxMessage() {
    }

    public OutboxMessage(String aggregateType, String aggregateId, String routingKey, String payload) {
        this.aggregateType = Objects.requireNonNull(aggregateType);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.routingKey = Objects.requireNonNull(routingKey);
        this.payload = Objects.requireNonNull(payload);
        this.attempts = 0;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markPublished() {
        this.publishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.attempts = this.attempts + 1;
    }

    public void markAttemptFailed() {
        this.attempts = this.attempts + 1;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public int getAttempts() {
        return attempts;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
