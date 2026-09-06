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
}
