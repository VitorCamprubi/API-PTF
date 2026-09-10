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

/**
 * Transacao e recurso de primeira classe, em /transactions, e nao um sub-recurso
 * de conta. A conta e um campo do lancamento, nao o dono dele: na etapa 12 a
 * transacao passa a ter ciclo de vida proprio (fila, status, reprocessamento) e
 * mais adiante uma transferencia envolve duas contas, o que nao caberia numa URL
 * pendurada em uma conta so.
 */
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
     * A chave de idempotencia vem em header, nao no corpo, porque nao e dado da
     * transacao: e metadado do protocolo. O corpo descreve o que o cliente quer;
     * o header descreve como esta tentativa deve ser tratada se ja tiver chegado.
     *
     * Ela e opcional por compatibilidade com o contrato que ja existe. Numa API
     * financeira de verdade seria obrigatoria, mas torna-la obrigatoria agora
     * quebraria todo cliente atual, e quebra de contrato pede versionamento.
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> create(
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody CreateTransactionRequest request) {

        TransactionService.Result result = transactionService.create(idempotencyKey, request);
        TransactionResponse body = TransactionResponse.from(result.transaction());

        // Replay nao cria nada, entao nao e 201. E 200 com o mesmo corpo da
        // primeira vez, mais um header dizendo que isto foi uma repeticao. O
        // cliente que so olha o status ve sucesso; o que investiga descobre o
        // que aconteceu de verdade.
        if (result.replay()) {
            return ResponseEntity.ok()
                    .header(IDEMPOTENT_REPLAY_HEADER, "true")
                    .body(body);
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.transaction().getId())
                .toUri();

        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{id}")
    public TransactionResponse findById(@PathVariable UUID id) {
        return TransactionResponse.from(transactionService.findById(id));
    }
}
