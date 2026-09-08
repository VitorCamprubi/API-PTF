package br.com.ptf.api.service;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.dto.CreateAccountRequest;
import br.com.ptf.api.exception.AccountNotFoundException;
import br.com.ptf.api.exception.DuplicateDocumentException;
import br.com.ptf.api.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account create(CreateAccountRequest request) {
        if (accountRepository.existsByDocument(request.document())) {
            throw new DuplicateDocumentException(request.document());
        }
        Account account = new Account(request.document(), request.holderName());
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account findById(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
