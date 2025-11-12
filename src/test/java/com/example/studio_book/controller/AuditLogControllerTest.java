// src/test/java/com/example/studio_book/controller/AuditLogControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.AuditLog;
import com.example.studio_book.repository.AuditLogRepository;

@WebMvcTest(AuditLogController.class)
class AuditLogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuditLogRepository auditLogRepository;

    private AuditLog log(int id, String action, String entity) {
        AuditLog al = new AuditLog();
        // 必要な最低限のフィールドだけセット（プロジェクトのエンティティ定義に合わせて調整）
        al.setId(id);
        al.setTs(LocalDateTime.now().minusMinutes(id));
        al.setAction(action);
        al.setEntity(entity);
        al.setActorId(100 + id);
        al.setEntityId(200 + id);
        al.setNote("note-" + id);
        return al;
    }

    @Nested
    @DisplayName("GET /admin/logs（デフォルト）")
    class DefaultList {

        @Test
        @WithMockUser(roles = "ADMIN")
        void ok_and_repository_called_with_spec_and_default_pageable() throws Exception {
            // Arrange
            Page<AuditLog> page = new PageImpl<>(List.of(
                log(1, "CREATE", "User"),
                log(2, "UPDATE", "Room")
            ));
            given(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);

            // Act & Assert
            mockMvc.perform(get("/admin/logs"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/logs"))
                .andExpect(model().attributeExists("logs"))
                .andExpect(model().attribute("q", (Object) null))
                .andExpect(model().attribute("actorId", (Object) null))
                .andExpect(model().attribute("action", (Object) null))
                .andExpect(model().attribute("entity", (Object) null))
                .andExpect(model().attribute("entityId", (Object) null))
                .andExpect(model().attribute("from", (Object) null))
                .andExpect(model().attribute("to", (Object) null));

            // Verify: Specification/Pageable の中身を検査
            ArgumentCaptor<Specification<AuditLog>> specCap = ArgumentCaptor.forClass(Specification.class);
            ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
            verify(auditLogRepository).findAll(specCap.capture(), pageableCap.capture());

            assertThat(specCap.getValue()).as("Specification should not be null").isNotNull();

            Pageable p = pageableCap.getValue();
            assertThat(p.getPageNumber()).isEqualTo(0);
            assertThat(p.getPageSize()).isEqualTo(10);
            assertThat(p.getSort().getOrderFor("ts")).isNotNull();
            assertThat(p.getSort().getOrderFor("ts").isDescending()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /admin/logs（クエリ付き）")
    class WithQueryParams {

        @Test
        @WithMockUser(roles = "ADMIN")
        void ok_and_params_are_kept_in_model_and_pageable_changes_with_page_param() throws Exception {
            // Arrange
            Page<AuditLog> page = new PageImpl<>(List.of(
                log(10, "DELETE", "Reservation")
            ));
            given(auditLogRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);

            // クエリパラメータ（全種）
            String q = "resv";
            int actorId = 7;
            String action = "delete";
            String entity = "reservation";
            int entityId = 1234;
            LocalDate from = LocalDate.of(2025, 11, 1);
            LocalDate to = LocalDate.of(2025, 11, 10);
            int pageIndex = 2;

            // Act & Assert
            mockMvc.perform(get("/admin/logs")
                    .param("q", q)
                    .param("actorId", String.valueOf(actorId))
                    .param("action", action)
                    .param("entity", entity)
                    .param("entityId", String.valueOf(entityId))
                    .param("from", from.toString())
                    .param("to", to.toString())
                    .param("page", String.valueOf(pageIndex)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/logs"))
                .andExpect(model().attributeExists("logs"))
                // 入力保持（そのまま model に返す設計）
                .andExpect(model().attribute("q", q))
                .andExpect(model().attribute("actorId", actorId))
                .andExpect(model().attribute("action", action))
                .andExpect(model().attribute("entity", entity))
                .andExpect(model().attribute("entityId", entityId))
                .andExpect(model().attribute("from", from))
                .andExpect(model().attribute("to", to));

            // Verify: Pageable が page=2,size=10, sort=ts DESC
            ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
            verify(auditLogRepository).findAll(any(Specification.class), pageableCap.capture());

            Pageable p = pageableCap.getValue();
            assertThat(p.getPageNumber()).isEqualTo(pageIndex);
            assertThat(p.getPageSize()).isEqualTo(10);
            assertThat(p.getSort().getOrderFor("ts")).isNotNull();
            assertThat(p.getSort().getOrderFor("ts").isDescending()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /admin/logs（未認証時）")
    class SecurityCase {
        @Test
        void unauthorized_without_login_if_security_applies() throws Exception {
            // プロジェクトのセキュリティ設定で /admin/** が保護対象なら 401/302 等になる。
            // ここでは 401 を期待にしておく（必要に応じて .is3xxRedirection へ変更）。
            mockMvc.perform(get("/admin/logs"))
                .andExpect(status().isUnauthorized());
        }
    }
}

