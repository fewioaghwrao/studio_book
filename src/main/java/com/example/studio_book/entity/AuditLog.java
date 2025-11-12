// src/main/java/com/example/studio_book/entity/AuditLog.java
package com.example.studio_book.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 操作のタイムスタンプ */
    @Column(nullable = false)
    private LocalDateTime ts;

    /** 実行者ID（User.id または Host.id） */
    @Min(value = 1, message = "actorId must be > 0")
    @Column(name = "actor_id", nullable = false)
    private Integer actorId;

    /** 操作の種類（例: host_reply, review_public_on 等） */
    @Column(nullable = false, length = 100)
    private String action;

    /** 対象のエンティティ種別（例: review, room, reservation） */
    @Column(nullable = false, length = 100)
    private String entity;

    /** 対象エンティティのID */
    @Min(value = 1, message = "entityId must be > 0")
    @Column(name = "entity_id")
    private Integer entityId;

    /** 任意: 詳細メモやIPなどを記録したい場合 */
    @Column(length = 255)
    private String note;
}
