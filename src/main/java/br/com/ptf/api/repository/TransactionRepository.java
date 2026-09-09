package br.com.ptf.api.repository;

import br.com.ptf.api.domain.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Busca a transacao ja com a conta carregada.
     *
     * Com open-in-view: false, a sessao do Hibernate fecha ao sair do service.
     * Se o controller tocar na conta depois disso, o proxy LAZY estoura
     * LazyInitializationException. A solucao nao e reabrir a sessao no
     * controller; e trazer na consulta o que a resposta vai precisar.
     */
    @Query("select t from Transaction t join fetch t.account where t.id = :id")
    Optional<Transaction> findByIdWithAccount(@Param("id") UUID id);
}
