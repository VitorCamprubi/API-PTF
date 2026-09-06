package br.com.ptf.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(

        @NotBlank(message = "documento e obrigatorio")
        @Pattern(regexp = "\\d{11}|\\d{14}", message = "documento deve ter 11 digitos (CPF) ou 14 (CNPJ)")
        String document,

        @NotBlank(message = "nome do titular e obrigatorio")
        @Size(max = 150, message = "nome do titular deve ter no maximo 150 caracteres")
        String holderName
) {
}
