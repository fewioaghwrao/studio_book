// src/test/java/com/example/studio_book/controller/AdminSettingsControllerTest.java
package com.example.studio_book.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.service.AdminSettingsService;

@WebMvcTest(AdminSettingsController.class)
class AdminSettingsControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AdminSettingsService adminSettingsService;

    @Nested
    class GetIndex {

        @Test
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        @DisplayName("GET /admin/settings : サービスから取得した小数(0.10/0.15)を%換算(10.00/15.00)でフォームに詰めて返す")
        void index_ok() throws Exception {
            // DB値（小数）
            when(adminSettingsService.getValue("tax_rate", "0")).thenReturn("0.10");
            when(adminSettingsService.getValue("admin_fee_rate", "0")).thenReturn("0.15");

            mockMvc.perform(get("/admin/settings"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/settings/index"))
                    .andExpect(model().attributeExists("form"))
                    // BigDecimalの比較はcomparesEqualToを使う
                    .andExpect(model().attribute("form",
                            hasProperty("taxRatePercent",
                                    comparesEqualTo(new BigDecimal("10.00")))))
                    .andExpect(model().attribute("form",
                            hasProperty("adminFeeRatePercent",
                                    comparesEqualTo(new BigDecimal("15.00")))));
        }
    }

    @Nested
    class PostUpdate {

        @Test
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        @DisplayName("POST /admin/settings 正常系: %入力を小数に変換して保存し、リダイレクト＆フラッシュメッセージ")
        void update_ok() throws Exception {
            // 入力は%（フォーム）。例: 10.00% → 0.10、12.3456% → 0.123456
            mockMvc.perform(
                    post("/admin/settings")
                            .param("taxRatePercent", "10.00")
                            .param("adminFeeRatePercent", "12.34") // ★ 2桁に
                            .with(csrf()) // CSRF必須
            )
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/settings"))
                    .andExpect(flash().attribute("successMessage", "保存しました"));

            // 10.00% → 0.10
            verify(adminSettingsService).updateValue("tax_rate", "0.1");
            // 12.34% → 0.1234
            verify(adminSettingsService).updateValue("admin_fee_rate", "0.1234");
        }

        @Test
        @WithMockUser(username = "admin", roles = {"ADMIN"})
        @DisplayName("POST /admin/settings 異常系: バリデーションNGならそのまま同ビューへ")
        void update_validation_error() throws Exception {
            // 例: 数値でない or 必須違反（@NotNull等）を想定
            mockMvc.perform(
                    post("/admin/settings")
                            .param("taxRatePercent", "")             // 未入力
                            .param("adminFeeRatePercent", "abc")     // 数値でない
                            .with(csrf())
            )
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/settings/index"))
                    .andExpect(model().attributeHasFieldErrors("form", "taxRatePercent", "adminFeeRatePercent"));
        }

        @Test
        @DisplayName("POST /admin/settings 未認証 or CSRF無しなら 401/403 になる（Security確認用）")
        void update_security() throws Exception {
            // 未ログイン（@WithMockUser無し）→ 302ログイン誘導 or 401/403 は環境設定に依存
            mockMvc.perform(
                    post("/admin/settings")
                            .param("taxRatePercent", "8")
                            .param("adminFeeRatePercent", "2")
                            // .with(csrf()) 付けない
            )
            .andExpect(status().is4xxClientError());
        }
    }
}
