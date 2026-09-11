package br.com.ptf.api.repository;

import br.com.ptf.api.domain.AuditAction;
import br.com.ptf.api.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    long countByAction(AuditAction action);

    List<AuditLog> findByEntityIdOrderByCreatedAtDesc(String entityId);
}
