package br.com.ptf.api.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Trava de exclusao mutua por identificador arbitrario, garantida pelo Postgres.
 *
 * Por que advisory lock e nao SELECT ... FOR UPDATE: nao existe linha para travar.
 * Na primeira requisicao de uma chave de idempotencia, a linha ainda nao foi
 * criada; e justamente a ausencia dela que duas threads disputam. FOR UPDATE trava
 * uma linha existente. O advisory lock trava um numero que voce escolhe, exista
 * linha ou nao.
 *
 * Por que a variante _xact_: ela e liberada automaticamente no commit ou no
 * rollback. A versao de sessao (pg_advisory_lock) exige unlock explicito, e numa
 * aplicacao com pool de conexoes uma trava esquecida volta para o pool presa a
 * conexao, envenenando a proxima requisicao que pegar aquela conexao.
 */
@Repository
public class AdvisoryLockRepository {

    /**
     * O Postgres devolve o tipo "void", que nao tem mapeamento util em JDBC.
     * A subconsulta existe so para o driver enxergar um inteiro na saida.
     */
    private static final String SQL =
            "SELECT 1 FROM (SELECT pg_advisory_xact_lock(:chave)) AS trava";

    private final EntityManager entityManager;

    public AdvisoryLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Bloqueia ate conseguir a trava, e so a solta quando a transacao atual
     * terminar. Tem que ser chamado dentro de uma transacao, senao a trava nasce
     * e morre na mesma instrucao, sem proteger nada.
     */
    public void lockUntilCommit(String namespace, String valor) {
        entityManager.createNativeQuery(SQL)
                .setParameter("chave", lockKeyOf(namespace, valor))
                .getSingleResult();
    }

    /**
     * O Postgres aceita um bigint como identificador da trava, entao qualquer
     * chave de texto precisa virar 64 bits. Usamos os primeiros 8 bytes de um
     * SHA-256 do namespace junto com o valor.
     *
     * Colisao e possivel e aceitavel: duas chaves diferentes que caiam no mesmo
     * numero apenas se serializam sem necessidade. Perde-se um pouco de paralelismo,
     * nunca a correcao. O namespace existe para que uma trava de idempotencia e
     * uma trava de outro assunto nunca se cruzem por acaso.
     */
    static long lockKeyOf(String namespace, String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((namespace + ":" + valor).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash, 0, Long.BYTES).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }
}
