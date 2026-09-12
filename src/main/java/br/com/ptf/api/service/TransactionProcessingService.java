package br.com.ptf.api.service;

import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.domain.Transaction;
import br.com.ptf.api.exception.AccountNotFoundException;
import br.com.ptf.api.exception.TransactionNotFoundException;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * O lado de la da fila: onde o dinheiro finalmente se move.
 */
@Service
public class TransactionProcessingService {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessingService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public TransactionProcessingService(TransactionRepository transactionRepository,
                                        AccountRepository accountRepository,
                                        AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    /**
     * Aplica o lancamento ao saldo, com a conta travada.
     *
     * A ORDEM DAS TRES PRIMEIRAS LINHAS E O CONTEUDO DESTA ETAPA.
     *
     * Travar a conta depois de te-la carregado nao resolve nada. Se duas threads
     * carregassem a conta primeiro, as duas teriam o saldo antigo no contexto de
     * persistencia; o lock chegaria tarde e cada uma gravaria o total que calculou
     * a partir da leitura velha. A ultima a commitar apagaria o trabalho da outra,
     * sem erro nenhum aparecer. Isso tem nome: lost update.
     *
     * Por isso primeiro descobrimos qual conta e (sem carrega-la), depois travamos
     * e carregamos, e so entao lemos o saldo. Quem chegar em segundo lugar fica
     * bloqueado no SELECT ... FOR UPDATE e, quando passar, le o saldo ja atualizado
     * pela primeira.
     *
     * A checagem de isPending continua: fila garante entrega pelo menos uma vez,
     * nao exatamente uma vez. Sem ela, uma reentrega creditaria de novo.
     */
    @Transactional
    public void process(UUID transactionId) {
        UUID contaId = transactionRepository.findAccountIdById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));

        accountRepository.findByIdForUpdate(contaId)
                .orElseThrow(() -> new AccountNotFoundException(contaId));

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
