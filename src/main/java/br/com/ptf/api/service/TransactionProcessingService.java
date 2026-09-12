package br.com.ptf.api.service;

import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.domain.Transaction;
import br.com.ptf.api.exception.TransactionNotFoundException;
import br.com.ptf.api.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * O lado de la da fila: onde o dinheiro finalmente se move.
 *
 * Separado do TransactionService de proposito. Aquele atende uma requisicao HTTP e
 * responde a um cliente que esta esperando; este atende uma mensagem e nao tem
 * ninguem do outro lado. As regras de erro sao diferentes: la, falhar significa
 * devolver um status; aqui, significa marcar o lancamento e seguir.
 */
@Service
public class TransactionProcessingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingService.class);

    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public TransactionProcessingService(TransactionRepository transactionRepository,
                                        AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    /**
     * Aplica o lancamento ao saldo.
     *
     * A checagem de isPending nao e detalhe: fila nao garante entrega unica,
     * garante entrega pelo menos uma vez. A mesma mensagem pode chegar duas vezes
     * porque o ack se perdeu, porque o consumidor caiu depois de processar e antes
     * de confirmar, ou porque alguem reenviou. Sem essa guarda, a segunda entrega
     * creditaria o valor de novo. E a mesma ideia da chave de idempotencia da
     * etapa 9, aplicada ao consumidor em vez do cliente.
     */
    @Transactional
    public void process(UUID transactionId) {
        Transaction transaction = transactionRepository.findByIdWithAccount(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        if (!transaction.isPending()) {
            log.debug("transacao {} ja esta em {}, ignorando reentrega",
                    transactionId, transaction.getStatus());
            return;
        }

        transaction.apply();
        transaction.markProcessed();

        auditService.record(
                AuditAction.TRANSACTION_PROCESSED,
                "Transaction",
                transactionId.toString(),
                "%s %s".formatted(transaction.getType(), transaction.getAmount()));
    }

    /**
     * Registra que o processamento falhou, em transacao propria.
     *
     * Chamado depois que a transacao de process ja voltou atras. Se isto rodasse
     * junto com aquela, o rollback apagaria tambem a explicacao da falha.
     */
    @Transactional
    public void markFailed(UUID transactionId, String reason) {
        transactionRepository.findById(transactionId).ifPresent(transaction -> {
            transaction.markFailed(reason);

            auditService.record(
                    AuditAction.TRANSACTION_FAILED,
                    "Transaction",
                    transactionId.toString(),
                    reason);
        });
    }
}
