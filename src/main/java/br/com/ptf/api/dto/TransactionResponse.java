package br.com.ptf.api.dto;

import br.com.ptf.api.domain.Transaction;
import br.com.ptf.api.domain.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Saida de POST e GET de transacao.
 *
 * Nao devolve o saldo resultante de proposito. Saldo e estado da conta, nao do
 * lancamento, e quem quiser o saldo tem GET /accounts/{id}/balance da etapa 6.
 * Alem disso, na etapa 12 o saldo passa a ser calculado de forma assincrona:
 * um campo "saldo depois" aqui viraria mentira, ou obrigaria a quebrar o
 * contrato justamente quando a arquitetura mudasse.
 */
public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String description,
        OffsetDateTime createdAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
