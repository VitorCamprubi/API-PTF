package br.com.ptf.api.repository;

import br.com.ptf.api.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Carrega a conta com lock pessimista de escrita: SELECT ... FOR UPDATE.
     *
     * A partir daqui, qualquer outra transacao que pedir esta mesma linha fica
     * bloqueada ate esta terminar. Nao e o mesmo que o advisory lock da etapa 10:
     * la o alvo era uma chave que ainda nao tinha virado linha; aqui a linha
     * existe, e travar a linha e mais direto e mais barato.
     *
     * Custo consciente: quem espera, espera de verdade, segurando uma conexao. Por
     * isso o lock e o mais curto possivel, dentro de uma transacao que faz pouca
     * coisa, e por conta e nao por tabela.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
