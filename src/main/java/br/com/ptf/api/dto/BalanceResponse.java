package br.com.ptf.api.dto;

import br.com.ptf.api.repository.AccountBalanceProjection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resposta do endpoint de saldo.
 *
 * Contrato proprio, separado de AccountResponse: quem consulta saldo nao recebe
 * documento nem nome do titular. Menos dado sensivel trafegando e um contrato que
 * pode evoluir sem arrastar junto o recurso conta.
 *
 * updatedAt responde "de quando e esse saldo", que e a pergunta seguinte de quem
 * consulta um valor que muda.
 */
public record BalanceResponse(
        UUID accountId,
        BigDecimal balance,
        OffsetDateTime updatedAt
) {

    public static BalanceResponse from(AccountBalanceProjection projection) {
        return new BalanceResponse(
                projection.getId(),
                projection.getBalance(),
                projection.getUpdatedAt()
        );
    }
}
