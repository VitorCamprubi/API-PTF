package br.com.ptf.api.service;

import br.com.ptf.api.domain.Account;
import br.com.ptf.api.domain.Transaction;
import br.com.ptf.api.dto.CreateTransactionRequest;
import br.com.ptf.api.exception.AccountNotFoundException;
import br.com.ptf.api.exception.TransactionNotFoundException;
import br.com.ptf.api.repository.AccountRepository;
import br.com.ptf.api.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Registra o lancamento e aplica o efeito no saldo, tudo numa transacao so.
     *
     * Repare no que NAO tem aqui: nenhum accountRepository.save(account). A conta
     * foi carregada dentro da transacao, entao esta gerenciada pelo Hibernate.
     * No commit, o dirty checking percebe que o saldo mudou e emite o UPDATE
     * sozinho. Chamar save() na conta seria redundante.
     *
     * O @Transactional e o que faz os dois efeitos serem um so: se o INSERT da
     * transacao falhar, o UPDATE do saldo volta atras junto. Sem isso, existiria
     * um instante em que o dinheiro mudou de valor sem lancamento que explique.
     */
    @Transactional
    public Transaction create(CreateTransactionRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        Transaction transaction = new Transaction(
                account,
                request.type(),
                request.amount(),
                request.description());

        transaction.apply();

        return transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public Transaction findById(UUID id) {
        return transactionRepository.findByIdWithAccount(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
