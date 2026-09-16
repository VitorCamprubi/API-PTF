package br.com.ptf.api.controller;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.dto.AccountResponse;
import br.com.ptf.api.dto.ApiError;
import br.com.ptf.api.dto.BalanceResponse;
import br.com.ptf.api.dto.CreateAccountRequest;
import br.com.ptf.api.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Contas", description = "Cadastro de contas e consulta de saldo")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @Operation(
            summary = "Cria uma conta",
            description = "O documento tem 11 digitos (CPF) ou 14 (CNPJ) e e unico. A conta nasce com saldo zero.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada; o header Location aponta para ela"),
            @ApiResponse(responseCode = "400", description = "Payload invalido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Ja existe conta com este documento",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.getId())
                .toUri();

        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta uma conta")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta encontrada"),
            @ApiResponse(responseCode = "404", description = "Conta inexistente",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AccountResponse findById(@PathVariable UUID id) {
        return AccountResponse.from(accountService.findById(id));
    }

    /**
     * Saldo como sub-recurso da conta.
     *
     * Cache-Control: no-store porque saldo e dado volatil e financeiro. Sem isso,
     * proxy ou browser pode guardar e devolver um valor velho como se fosse atual.
     */
    @GetMapping("/{id}/balance")
    @Operation(
            summary = "Consulta o saldo",
            description = """
                    Endpoint separado de GET /accounts/{id} porque saldo muda com frequencia
                    muito maior que os dados cadastrais, e quem consulta saldo nao precisa
                    receber documento e titular junto. A resposta nunca e cacheavel.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo atual da conta"),
            @ApiResponse(responseCode = "404", description = "Conta inexistente",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<BalanceResponse> findBalance(@PathVariable UUID id) {
        BalanceResponse body = BalanceResponse.from(accountService.findBalanceById(id));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
