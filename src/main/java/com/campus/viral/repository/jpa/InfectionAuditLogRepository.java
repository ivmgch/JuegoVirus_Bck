package com.campus.viral.repository.jpa;

import com.campus.viral.model.entity.InfectionAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InfectionAuditLogRepository extends JpaRepository<InfectionAuditLog, Long> {

    List<InfectionAuditLog> findBySessionIdOrderByCreatedAtDesc(Long sessionId);
}
