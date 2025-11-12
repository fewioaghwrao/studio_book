// src/main/java/com/example/studio_book/service/AdminSettingsService.java
package com.example.studio_book.service;

import org.springframework.stereotype.Service;

import com.example.studio_book.entity.AdminSettings;
import com.example.studio_book.repository.AdminSettingsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminSettingsService {

    private final AdminSettingsRepository repo;

    public String getValue(String key) {
        return repo.findByKey(key).map(AdminSettings::getValue).orElse("");
    }

    public String getValue(String key, String defaultValue) {
        return repo.findByKey(key).map(AdminSettings::getValue).orElse(defaultValue);
    }

    public void updateValue(String key, String value) {
        AdminSettings s = repo.findByKey(key).orElseGet(() -> new AdminSettings(key, value));
        s.setValue(value);
        repo.save(s);
    }
}
