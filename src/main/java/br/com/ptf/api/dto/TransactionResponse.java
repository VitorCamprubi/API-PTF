package br.com.ptf.api.dto;

import br.com.ptf.api.domain.Transaction;
import br.com.ptf.api.domain.TransactionStatus;
import br.com.ptf.api.domain.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Saida de POST e GET de transacao.
 *
 * O campo status entrou nesta etapa e e a mudanca de contrato mais importante do
 * projeto ate agora: o cliente que recebe 202 precisa saber que o lancamento foi
 * aceito mas ainda nao aplicado, e precisa de um lugar para consultar em que pe
 * ficou. Sem status, "aceito" e "concluido" ficariam indistinguiveis.
 *
 * Continua sem devolver saldo: saldo e estado da conta, nao do lancamento, e
 * agora seria mentira na maior parte das vezes, porque no instante da resposta
 * ele ainda nao mudou. Quem quer saldo tem GET /accounts/{id}/balance.
 */
public record TransactionResponse(
        UUID id,
        UUID accountId,
        TransactionType type,
        BigDecimal amount,
        String description,
        TransactionStatus status,
        String failureReason,
        OffsetDateTime createdAt,
        OffsetDateTime processedAt
) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getFailureReason(),
                transaction.getCreatedAt(),
                transaction.getProcessedAt()
        );
    }
}
