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
import java.time.ZoneOffset;
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

    /**
     * Timestamps sempre em UTC.
     *
     * A coluna e TIMESTAMPTZ: o Postgres guarda o instante e descarta o fuso de
     * origem. Gravar com o fuso local fazia o mesmo instante aparecer como
     * -03:00 no objeto em memoria e como Z depois de ler do banco. A API fala
     * UTC; converter para o fuso do usuario e problema de quem exibe.
     */
    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Credita um valor no saldo.
     *
     * O saldo so muda por aqui e pelo debit. Nao existe setBalance: alterar
     * saldo exige passar por uma operacao de dominio, com nome e com regra.
     */
    public void credit(BigDecimal amount) {
        requirePositive(amount);
        this.balance = this.balance.add(amount).setScale(MONETARY_SCALE);
    }

    /**
     * Debita um valor do saldo.
     *
     * Ainda nao ha verificacao de saldo suficiente. Hoje quem impede saldo
     * negativo e a constraint chk_accounts_balance_non_negative, que derruba a
     * transacao inteira no commit. A regra explicita, com lock, entra na etapa 13.
     */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        this.balance = this.balance.subtract(amount).setScale(MONETARY_SCALE);
    }

    /**
     * Guarda contra erro de programacao, nao contra entrada do usuario. O valor
     * ja chega validado pelo DTO; se chegar invalido aqui, e bug nosso.
     */
    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("valor da operacao deve ser positivo");
        }
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
