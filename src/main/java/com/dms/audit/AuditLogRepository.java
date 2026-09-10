package com.dms.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByAtDesc(Pageable pageable);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByAtDesc(String entityType, Long entityId);

    long countByAction(String action);
}
