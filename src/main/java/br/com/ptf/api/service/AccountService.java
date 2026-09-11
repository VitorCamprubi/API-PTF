package br.com.ptf.api.service;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.dto.CreateAccountRequest;
import br.com.ptf.api.exception.AccountNotFoundException;
import br.com.ptf.api.exception.DuplicateDocumentException;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.AccountBalanceProjection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public AccountService(AccountRepository accountRepository, AuditService auditService) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Account create(CreateAccountRequest request) {
        if (accountRepository.existsByDocument(request.document())) {
            throw new DuplicateDocumentException(request.document());
        }
        Account account = new Account(request.document(), request.holderName());
        Account salva = accountRepository.save(account);

        auditService.record(AuditAction.ACCOUNT_CREATED, "Account", salva.getId().toString(), null);

        return salva;
    }

    @Transactional(readOnly = true)
    public Account findById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    /**
     * Consulta de saldo.
     *
     * Conta inexistente e regra de negocio, nao Optional vazio devolvido ao
     * controller: a excecao de dominio sobe e o GlobalExceptionHandler decide o
     * status HTTP. O service continua sem saber que existe HTTP.
     */
    @Transactional(readOnly = true)
    public AccountBalanceProjection findBalanceById(UUID id) {
        return accountRepository.findBalanceById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
