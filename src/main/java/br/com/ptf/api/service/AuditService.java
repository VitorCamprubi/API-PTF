package br.com.ptf.api.service;

import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.domain.AuditLog;
import br.com.ptf.api.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrita da trilha de auditoria.
 *
 * A classe tem dois metodos que fazem a mesma coisa e se comportam de forma
 * oposta. A diferenca esta na propagacao, e ela e a decisao de desenho desta
 * etapa inteira.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Audita uma operacao bem-sucedida, dentro da transacao de quem chamou.
     *
     * MANDATORY significa: exijo uma transacao ja aberta e me junto a ela; se nao
     * houver, estouro. Nao e capricho. O registro de auditoria de uma operacao
     * financeira precisa nascer e morrer junto com ela. Se a operacao volta atras,
     * o registro tem que voltar junto, senao a trilha afirma que aconteceu algo
     * que nao aconteceu. E se alguem chamar este metodo fora de transacao achando
     * que auditou, o MANDATORY avisa em vez de gravar um log solto.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String entityType, String entityId, String detail) {
        auditLogRepository.save(new AuditLog(action, entityType, entityId, detail));
    }

    /**
     * Audita uma tentativa que vai ser recusada, em transacao propria.
     *
     * REQUIRES_NEW suspende a transacao de quem chamou, abre outra, commita essa e
     * devolve o controle. O registro sobrevive mesmo quando a operacao original da
     * rollback logo em seguida, que e exatamente o caso de uma tentativa recusada:
     * o fato relevante e que alguem tentou.
     *
     * Custo real: a transacao nova toma uma segunda conexao do pool enquanto a
     * primeira continua segurando a dela. Usar isso em caminho quente esgota o pool.
     * Aqui vale porque o caminho e de excecao.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInNewTransaction(AuditAction action, String entityType, String entityId, String detail) {
        auditLogRepository.save(new AuditLog(action, entityType, entityId, detail));
    }
}
