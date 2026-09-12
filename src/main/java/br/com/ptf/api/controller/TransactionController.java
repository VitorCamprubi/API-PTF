package br.com.ptf.api.controller;

import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.dto.TransactionResponse;
import br.com.ptf.api.service.TransactionService;
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

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String IDEMPOTENT_REPLAY_HEADER = "Idempotent-Replay";

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Agora devolve 202 Accepted, nao mais 201 Created.
     *
     * A diferenca nao e cosmetica. 201 significa "o recurso existe e esta pronto";
     * 202 significa "aceitei o pedido, o resultado vem depois". Devolver 201 num
     * fluxo assincrono e mentir para o cliente: ele consultaria o saldo logo em
     * seguida, veria o valor antigo e concluiria que a API perdeu a operacao.
     *
     * O header Location continua, e agora tem uma funcao a mais: e o endereco para
     * o cliente acompanhar o status ate sair de PENDING.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
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
    public TransactionResponse findById(@PathVariable UUID id) {
        return TransactionResponse.from(transactionService.findById(id));
    }
}
