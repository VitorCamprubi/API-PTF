package br.com.ptf.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentacao gerada a partir do codigo, nao escrita ao lado dele.
 *
 * Documentacao em arquivo separado comeca correta e envelhece em silencio: alguem
 * muda o contrato, esquece do documento, e a partir dali ele passa a mentir. Aqui
 * a especificacao OpenAPI e derivada das anotacoes dos controllers e dos DTOs a
 * cada subida, entao ela erra junto com o codigo, e nunca sozinha.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiPtfOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("API PTF")
                .description("""
                        API de processamento de transacoes financeiras.

                        O lancamento e aceito de forma sincrona e aplicado ao saldo de forma
                        assincrona: POST /transactions responde 202 com status PENDING, e o
                        saldo muda quando o consumidor processa a mensagem. Consulte o status
                        em GET /transactions/{id} e o saldo em GET /accounts/{id}/balance.

                        Envie o header Idempotency-Key em POST /transactions para que um retry
                        nao vire um segundo lancamento.
                        """)
                .version("1.0.0")
                .contact(new Contact().name("Vitor Camprubi").url("https://github.com/VitorCamprubi"))
                .license(new License().name("MIT")));
    }
}
