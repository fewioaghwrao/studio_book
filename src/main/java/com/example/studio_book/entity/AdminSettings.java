package com.example.studio_book.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "admin_settings",
       uniqueConstraints = @UniqueConstraint(name = "uq_admin_settings_key", columnNames = "key"))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // SQL予約語のためバッククォート名を指定
    @Column(name = "`key`", nullable = false)
    private String key;

    @Column(name = "`value`", nullable = false)
    private String value;

    // DB側の DEFAULT CURRENT_TIMESTAMP / ON UPDATE を使うなら insert/updateしない
    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public AdminSettings(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
