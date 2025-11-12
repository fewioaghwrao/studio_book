package com.example.studio_book.entity;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_room_user", columnNames = {"room_id", "user_id"})
    }
)
@Getter @Setter
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // rooms.id 外部キー
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    // users.id 外部キー
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // DBのDEFAULT利用（アプリから更新しない）
    @Column(name = "created_at", insertable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Timestamp updatedAt;
    
    // ▼ 追加（返信/公開）
    @Column(name = "host_reply", columnDefinition = "TEXT")
    private String hostReply;

    @Column(name = "host_reply_at")
    private LocalDateTime hostReplyAt;

    @Column(name = "is_public", nullable = false)
    private Boolean publicVisible = true;

    @Column(name = "hidden_reason", length = 255, nullable = true)
    private String hiddenReason;
}


