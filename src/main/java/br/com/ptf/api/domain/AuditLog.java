package br.com.ptf.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Registro de algo que aconteceu.
 *
 * Imutavel, como Transaction: log que pode ser editado nao serve de log.
 *
 * entityId e texto, nao UUID, porque nem toda entidade auditada tem UUID por
 * identidade. Um conflito de idempotencia e sobre uma chave, que e uma string
 * escolhida pelo cliente. Uma auditoria generica demais nao serve, mas uma presa
 * ao formato de id de uma entidade so serve menos ainda.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 50, updatable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 100, updatable = false)
    private String entityId;

    /**
     * Texto curto e descritivo. Nao entra aqui documento, nome do titular nem
     * nada que identifique pessoa: auditoria costuma ser lida por muito mais gente
     * do que a tabela de origem, e dado pessoal espalhado e vazamento esperando
     * acontecer. Se um dia for preciso consultar por dentro do detalhe, o caminho
     * e trocar por JSONB, nao empilhar texto.
     */
    @Column(name = "detail", length = 500, updatable = false)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {
    }

    public AuditLog(AuditAction action, String entityType, String entityId, String detail) {
        this.action = Objects.requireNonNull(action, "acao e obrigatoria");
        this.entityType = Objects.requireNonNull(entityType, "tipo da entidade e obrigatorio");
        this.entityId = Objects.requireNonNull(entityId, "id da entidade e obrigatorio");
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDetail() {
        return detail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
