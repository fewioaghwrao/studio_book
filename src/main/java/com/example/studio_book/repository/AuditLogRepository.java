// src/main/java/com/example/studio_book/repository/AuditLogRepository.java
package com.example.studio_book.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.example.studio_book.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Integer>,
                                           JpaSpecificationExecutor<AuditLog> { // ★ 追加

    default void add(String action, Integer actorId, String entity, Integer entityId) {
        AuditLog log = AuditLog.builder()
                .ts(java.time.LocalDateTime.now())
                .action(action)
                .actorId(actorId)
                .entity(entity)
                .entityId(entityId)
                .build();
        save(log);
    }
}
