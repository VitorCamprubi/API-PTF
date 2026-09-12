package br.com.ptf.api.service;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.domain.IdempotencyRecord;
import br.com.ptf.api.domain.Transaction;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.exception.AccountNotFoundException;
import br.com.ptf.api.exception.IdempotencyConflictException;
import br.com.ptf.api.exception.TransactionNotFoundException;
import br.com.ptf.api.messaging.TransactionMessage;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.AdvisoryLockRepository;
import br.com.ptf.api.repository.IdempotencyRecordRepository;
import br.com.ptf.api.repository.TransactionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * O lado de ca da fila: recebe o pedido, valida, registra e aceita.
 *
 * O que este service NAO faz mais: mexer no saldo. Ate a etapa 11 ele aplicava o
 * valor na conta na mesma transacao. Agora ele apenas registra o lancamento como
 * PENDING e avisa que ha trabalho a fazer. Quem move dinheiro e o
 * TransactionProcessingService, do outro lado do broker.
 */
@Service
public class TransactionService {

    private static final String LOCK_NAMESPACE = "idempotency";

    public record Result(Transaction transaction, boolean replay) {
    }

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AdvisoryLockRepository advisoryLockRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              AdvisoryLockRepository advisoryLockRepository,
                              AuditService auditService,
                              ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.advisoryLockRepository = advisoryLockRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Result create(String idempotencyKey, CreateTransactionRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new Result(registrar(request), false);
        }

        String chave = idempotencyKey.trim();

        advisoryLockRepository.lockUntilCommit(LOCK_NAMESPACE, chave);

        String hash = hashOf(request);

        Optional<IdempotencyRecord> registro = idempotencyRecordRepository.findById(chave);
        if (registro.isPresent()) {
            if (!registro.get().getRequestHash().equals(hash)) {
                auditService.recordInNewTransaction(
                        AuditAction.IDEMPOTENCY_CONFLICT,
                        "IdempotencyKey",
                        chave,
                        "payload diferente do registrado para esta chave");

                throw new IdempotencyConflictException(chave);
            }
            UUID idOriginal = registro.get().getTransactionId();
            Transaction original = transactionRepository.findByIdWithAccount(idOriginal)
                    .orElseThrow(() -> new TransactionNotFoundException(idOriginal));

            auditService.record(
                    AuditAction.TRANSACTION_REPLAYED,
                    "Transaction",
                    idOriginal.toString(),
                    "chave " + chave);

            return new Result(original, true);
        }

        Transaction transaction = registrar(request);

        idempotencyRecordRepository.save(new IdempotencyRecord(chave, hash, transaction.getId()));

        return new Result(transaction, false);
    }

    @Transactional(readOnly = true)
    public Transaction findById(UUID id) {
        return transactionRepository.findByIdWithAccount(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    /**
     * Registra o lancamento e pede para processarem.
     *
     * A conta continua sendo verificada aqui, de forma sincrona: conta inexistente
     * e erro do cliente e ele merece saber na hora, com 404, em vez de receber 202
     * e descobrir depois que o lancamento morreu. Validar o que da para validar
     * antes de aceitar e o que separa assincrono de irresponsavel.
     *
     * O publishEvent nao fala com o RabbitMQ. Ele registra um evento interno que o
     * Spring so entrega depois do commit desta transacao. Quem fala com o broker e
     * o TransactionPublisher, e o porque esta la.
     */
    private Transaction registrar(CreateTransactionRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        Transaction transaction = new Transaction(
                account,
                request.type(),
                request.amount(),
                request.description());

        Transaction salva = transactionRepository.save(transaction);

        auditService.record(
                AuditAction.TRANSACTION_CREATED,
                "Transaction",
                salva.getId().toString(),
                "%s %s".formatted(salva.getType(), salva.getAmount()));

        eventPublisher.publishEvent(new TransactionMessage(salva.getId()));

        return salva;
    }

    private static String hashOf(CreateTransactionRequest request) {
        String canonico = "%s|%s|%s|%s".formatted(
                request.accountId(),
                request.type(),
                request.amount().setScale(Account.MONETARY_SCALE),
                request.description() == null ? "" : request.description());

        return sha256Hex(canonico);
    }

    private static String sha256Hex(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
