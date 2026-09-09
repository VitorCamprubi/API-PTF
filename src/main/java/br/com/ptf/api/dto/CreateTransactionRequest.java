package br.com.ptf.api.dto;

import br.com.ptf.api.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entrada de POST /transactions.
 *
 * O valor chega como BigDecimal e e validado antes de qualquer regra rodar:
 * DecimalMin barra zero e negativo, Digits barra mais de quatro casas decimais.
 * Sem o Digits, um valor com seis casas seria truncado em algum ponto entre a
 * aplicacao e o NUMERIC(19,4) do banco, e truncamento silencioso de dinheiro e
 * exatamente o tipo de bug que ninguem descobre no mesmo dia.
 */
public record CreateTransactionRequest(

        @NotNull(message = "id da conta e obrigatorio")
        UUID accountId,

        @NotNull(message = "tipo e obrigatorio")
        TransactionType type,

        @NotNull(message = "valor e obrigatorio")
        @DecimalMin(value = "0.0001", message = "valor deve ser maior que zero")
        @Digits(integer = 15, fraction = 4, message = "valor deve ter no maximo 4 casas decimais")
        BigDecimal amount,

        @Size(max = 255, message = "descricao deve ter no maximo 255 caracteres")
        String description
) {
}
