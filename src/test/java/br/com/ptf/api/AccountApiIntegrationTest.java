package br.com.ptf.api;

import br.com.ptf.api.dto.CreateAccountRequest;
import br.com.ptf.api.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private AccountRepository accountRepository;


    @Test
    @DisplayName("cria conta e devolve 201 com header Location")
    void criaConta() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequest("12345678901", "Vitor Camprubi"))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.document").value("12345678901"))
                .andExpect(jsonPath("$.holderName").value("Vitor Camprubi"));

        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("documento repetido devolve 409")
    void documentoRepetido() throws Exception {
        String corpo = json(new CreateAccountRequest("12345678901", "Vitor Camprubi"));

        mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(accountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("documento invalido devolve 400 com o campo que falhou")
    void documentoInvalido() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateAccountRequest("123", "Vitor Camprubi"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("document"));

        assertThat(accountRepository.count()).isZero();
    }

    @Test
    @DisplayName("conta inexistente devolve 404")
    void contaInexistente() throws Exception {
        mockMvc.perform(get("/accounts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
