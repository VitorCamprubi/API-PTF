package br.com.ptf.api.repository;

import br.com.ptf.api.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByDocument(String document);

    boolean existsByDocument(String document);

    /**
     * Le apenas as colunas necessarias para responder o saldo.
     *
     * O trecho entre "find" e "By" e ignorado pelo Spring Data na derivacao da
     * query; serve so para dar nome ao metodo. Quem determina as colunas do SELECT
     * e o tipo de retorno AccountBalanceProjection.
     */
    Optional<AccountBalanceProjection> findBalanceById(UUID id);
}
