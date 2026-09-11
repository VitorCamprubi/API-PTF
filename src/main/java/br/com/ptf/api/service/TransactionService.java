package br.com.ptf.api.service;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.domain.IdempotencyRecord;
import br.com.ptf.api.domain.Transaction;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.exception.AccountNotFoundException;
import br.com.ptf.api.exception.IdempotencyConflictException;
import br.com.ptf.api.exception.TransactionNotFoundException;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.AdvisoryLockRepository;
import br.com.ptf.api.repository.IdempotencyRecordRepository;
import br.com.ptf.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private static final String LOCK_NAMESPACE = "idempotency";

    /**
     * Resultado da criacao. O controller precisa saber se houve replay para
     * escolher entre 201 Created e 200 OK, e o service nao pode conhecer status
     * HTTP. Um booleano resolve sem quebrar a separacao.
     */
    public record Result(Transaction transaction, boolean replay) {
    }

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AdvisoryLockRepository advisoryLockRepository;
    private final AuditService auditService;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              AdvisoryLockRepository advisoryLockRepository,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.advisoryLockRepository = advisoryLockRepository;
        this.auditService = auditService;
    }

    /**
     * Cria a transacao respeitando a chave de idempotencia, quando informada.
     *
     * Tres caminhos possiveis:
     *   1. chave nunca vista        -> processa e registra a chave
     *   2. chave vista, mesmo hash  -> devolve a transacao original, sem processar
     *   3. chave vista, hash outro  -> 409, porque a mesma chave esta descrevendo
     *                                  duas operacoes diferentes
     *
     * A trava na primeira linha e o que torna esses tres caminhos confiaveis. Sem
     * ela, entre a consulta e a gravacao existe uma janela em que outra thread com
     * a mesma chave tambem consulta, tambem nao encontra nada, e tambem processa.
     * O resultado era dinheiro duplicado por alguns milissegundos, com o rollback
     * da segunda thread limpando por acidente, nao por desenho.
     *
     * A trava e por chave, nao global: requisicoes com chaves diferentes continuam
     * rodando em paralelo sem se enxergar.
     */
    @Transactional
    public Result create(String idempotencyKey, CreateTransactionRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new Result(process(request), false);
        }

        String chave = idempotencyKey.trim();

        advisoryLockRepository.lockUntilCommit(LOCK_NAMESPACE, chave);

        String hash = hashOf(request);

        Optional<IdempotencyRecord> registro = idempotencyRecordRepository.findById(chave);
        if (registro.isPresent()) {
            if (!registro.get().getRequestHash().equals(hash)) {
                // Em transacao propria: daqui a duas linhas esta transacao vai dar
                // rollback, e o registro de que alguem reutilizou uma chave com
                // outro payload e justamente o que nao pode se perder.
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

        Transaction transaction = process(request);

        // O id da transacao ja existe aqui, antes de qualquer INSERT chegar ao
        // banco, porque na etapa 3 escolhemos GenerationType.UUID. Se o id fosse
        // sequence do banco, este registro nao teria o que gravar ainda.
        idempotencyRecordRepository.save(new IdempotencyRecord(chave, hash, transaction.getId()));

        return new Result(transaction, false);
    }

    @Transactional(readOnly = true)
    public Transaction findById(UUID id) {
        return transactionRepository.findByIdWithAccount(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private Transaction process(CreateTransactionRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        Transaction transaction = new Transaction(
                account,
                request.type(),
                request.amount(),
                request.description());

        transaction.apply();

        Transaction salva = transactionRepository.save(transaction);

        // Na mesma transacao do lancamento. Se o saldo nao puder ser gravado, o
        // rollback leva a auditoria junto: nao existe "registrei que aconteceu"
        // sem ter acontecido.
        auditService.record(
                AuditAction.TRANSACTION_CREATED,
                "Transaction",
                salva.getId().toString(),
                "%s %s".formatted(salva.getType(), salva.getAmount()));

        return salva;
    }

    /**
     * Resumo canonico do pedido.
     *
     * O valor e normalizado na escala monetaria antes de entrar no hash, de
     * proposito: "100.5" e "100.5000" sao o mesmo dinheiro, entao tem que gerar o
     * mesmo hash. Se a gente fizesse hash do JSON cru, um cliente que reenviasse
     * o mesmo pedido com formatacao diferente levaria 409 sem ter mudado nada.
     */
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
