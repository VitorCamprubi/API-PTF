package br.com.ptf.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

/**
 * Um lancamento financeiro.
 *
 * Os fatos financeiros continuam imutaveis: conta, tipo, valor e descricao sao
 * updatable = false e nao tem setter. O que passou a mudar foi o estado de
 * PROCESSAMENTO, e so ele. A distincao importa: corrigir um valor errado continua
 * exigindo um estorno; registrar que o lancamento saiu de PENDING para PROCESSED
 * e apenas contar o que aconteceu com ele.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    private static final int MAX_FAILURE_REASON = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * LAZY de proposito. Com EAGER, toda leitura de transacao arrastaria a conta
     * junto, mesmo quando ninguem pede a conta. EAGER e uma decisao tomada na
     * entidade que voce nao consegue desfazer no ponto de uso; LAZY voce resolve
     * com join fetch onde precisar.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    /**
     * STRING, nunca ORDINAL. Com ORDINAL o banco guarda 0 e 1, e o dia em que
     * alguem inserir um valor novo no meio do enum, todo o historico muda de
     * significado sem nenhum erro aparecer.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "failure_reason", length = MAX_FAILURE_REASON)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Transaction() {
    }

    public Transaction(Account account, TransactionType type, BigDecimal amount, String description) {
        this.account = Objects.requireNonNull(account, "conta e obrigatoria");
        this.type = Objects.requireNonNull(type, "tipo e obrigatorio");
        this.amount = Objects.requireNonNull(amount, "valor e obrigatorio")
                .setScale(Account.MONETARY_SCALE);
        this.description = description;
        this.status = TransactionStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Aplica o efeito desta transacao no saldo da conta.
     *
     * Quem sabe o efeito e o tipo, nao o service. O service so orquestra.
     */
    public void apply() {
        type.applyTo(account, amount);
    }

    public void markProcessed() {
        this.status = TransactionStatus.PROCESSED;
        this.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = TransactionStatus.FAILED;
        this.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.failureReason = truncate(reason);
    }

    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_FAILURE_REASON
                ? reason
                : reason.substring(0, MAX_FAILURE_REASON);
    }

    public UUID getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Transaction transaction)) {
            return false;
        }
        return id != null && id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }

    @Override
    public String toString() {
        return "Transaction{id=%s, type=%s, amount=%s, status=%s}".formatted(id, type, amount, status);
    }
}
