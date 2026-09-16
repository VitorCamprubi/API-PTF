package br.com.ptf.api.controller;

import br.com.ptf.api.dto.ApiError;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.dto.TransactionResponse;
import br.com.ptf.api.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Transacao e recurso de primeira classe, em /transactions, e nao um sub-recurso
 * de conta. A conta e um campo do lancamento, nao o dono dele.
 */
@RestController
@RequestMapping("/transactions")
@Tag(name = "Transacoes", description = "Lancamentos financeiros: criacao assincrona e consulta de status")
public class TransactionController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replay";

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @Operation(
            summary = "Registra um lancamento",
            description = """
                    Responde 202 Accepted, e nao 201: o lancamento e gravado com status PENDING
                    e o saldo so muda quando o consumidor processa a mensagem. Consulte
                    GET /transactions/{id} para acompanhar ate PROCESSED ou FAILED.

                    Com o header Idempotency-Key, reenviar a mesma requisicao devolve o
                    lancamento original com 200 e o header Idempotent-Replay, em vez de criar
                    um segundo. A mesma chave com payload diferente e recusada com 409.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Lancamento aceito e aguardando processamento"),
            @ApiResponse(responseCode = "200", description = "Replay: a chave ja tinha sido usada com este mesmo payload"),
            @ApiResponse(responseCode = "400", description = "Payload invalido",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Conta inexistente",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Chave de idempotencia reutilizada com outro payload",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TransactionResponse> create(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false)
            @Parameter(description = "Identificador da tentativa, escolhido pelo cliente. Recomendado em toda requisicao.")
            String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {

        TransactionService.Result result = transactionService.create(idempotencyKey, request);
        TransactionResponse body = TransactionResponse.from(result.transaction());

        if (result.replay()) {
            return ResponseEntity.ok()
                    .header(IDEMPOTENT_REPLAY_HEADER, "true")
                    .body(body);
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.transaction().getId())
                .toUri();

        return ResponseEntity.accepted().location(location).body(body);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consulta um lancamento",
            description = "Use para acompanhar o status. Em FAILED, o campo failureReason explica o motivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lancamento encontrado"),
            @ApiResponse(responseCode = "404", description = "Lancamento inexistente",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public TransactionResponse findById(@PathVariable UUID id) {
        return TransactionResponse.from(transactionService.findById(id));
    }
}
