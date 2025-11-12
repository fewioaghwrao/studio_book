package com.example.studio_book.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.studio_book.entity.AdminSettings;

public interface AdminSettingsRepository extends JpaRepository<AdminSettings, Long> {
    Optional<AdminSettings> findByKey(String key);
}
