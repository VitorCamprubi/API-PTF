package br.com.ptf.api.repository;

import br.com.ptf.api.domain.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /**
     * Pega um lote de pendentes e trava as linhas, pulando as que outro ja pegou.
     *
     * SKIP LOCKED e o que torna isto seguro com mais de uma instancia da aplicacao
     * rodando. Sem ele, duas instancias leriam as mesmas linhas e publicariam as
     * mesmas mensagens; com FOR UPDATE sozinho, a segunda instancia ficaria parada
     * esperando a primeira terminar o lote inteiro, desperdicando o paralelismo.
     * Com SKIP LOCKED, a segunda simplesmente ignora o que ja esta travado e pega
     * o proximo lote. E a diferenca entre uma fila de trabalho e uma fila de
     * espera.
     *
     * Consulta nativa porque FOR UPDATE SKIP LOCKED nao existe em JPQL.
     */
    @Query(value = """
            SELECT * FROM outbox_messages
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessage> travarPendentes(@Param("limite") int limite);

    long countByPublishedAtIsNull();
}
