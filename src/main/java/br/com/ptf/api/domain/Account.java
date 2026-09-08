package br.com.ptf.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    /**
     * Escala monetaria do projeto. Espelha o NUMERIC(19, 4) do banco.
     *
     * Todo BigDecimal de dinheiro criado em memoria e normalizado nesta escala,
     * para que o valor serializado no JSON nao dependa da origem do objeto:
     * saldo recem-criado e saldo lido do banco saem os dois como 0.0000.
     */
    public static final int MONETARY_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document", nullable = false, unique = true, length = 14, updatable = false)
    private String document;

    @Column(name = "holder_name", nullable = false, length = 150)
    private String holderName;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * Exigido pelo JPA. Protegido para que o resto da aplicacao nao consiga
     * criar uma conta em estado invalido.
     */
    protected Account() {
    }

    public Account(String document, String holderName) {
        this.document = document;
        this.holderName = holderName;
        this.balance = BigDecimal.ZERO.setScale(MONETARY_SCALE);
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getDocument() {
        return document;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account account)) {
            return false;
        }
        return id != null && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }

    @Override
    public String toString() {
        return "Account{id=%s, document='%s', holderName='%s', balance=%s}"
                .formatted(id, document, holderName, balance);
    }
}
