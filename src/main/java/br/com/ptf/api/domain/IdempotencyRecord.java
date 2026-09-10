package br.com.ptf.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de uma chave de idempotencia ja usada.
 *
 * A chave e a propria identidade: e ela que o cliente manda, e ela que precisa
 * ser unica, entao ela e a chave primaria. Nao existe UUID artificial aqui, ao
 * contrario de Account e Transaction, porque nao havia identidade natural la e
 * aqui ha.
 *
 * transactionId e um UUID solto, sem @ManyToOne. Esta tabela e infraestrutura de
 * protocolo, nao dominio: ela nao participa de regra de negocio nenhuma e nao
 * precisa navegar ate a transacao. Guardar so o identificador deixa isso explicito.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    /**
     * SHA-256 do payload em formato canonico, em hexadecimal (64 caracteres).
     *
     * Guardamos o hash e nao o corpo original por dois motivos: o corpo pode ser
     * grande, e o corpo pode conter dado que nao queremos duplicar no banco. Para
     * responder "e o mesmo pedido de antes?", comparar o resumo basta.
     */
    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String idempotencyKey, String requestHash, UUID transactionId) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "chave e obrigatoria");
        this.requestHash = Objects.requireNonNull(requestHash, "hash e obrigatorio");
        this.transactionId = Objects.requireNonNull(transactionId, "id da transacao e obrigatorio");
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
