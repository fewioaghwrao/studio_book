// src/test/java/com/example/studio_book/controller/HostProfileControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.Role;
import com.example.studio_book.entity.User;
import com.example.studio_book.form.UserEditForm;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.UserService;

@WebMvcTest(
        controllers = HostProfileController.class,
        excludeAutoConfiguration = { ThymeleafAutoConfiguration.class } // ★ テンプレ描画を切る
)
class HostProfileControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    UserService userService;

    private User buildUser() {
        User u = new User();
        // 必要な項目だけセット（テストが依存するもの）
        u.setId(10); // プリミティブ/ラッパ型はプロジェクトの定義に合わせて
        u.setName("山田太郎");
        u.setFurigana("ヤマダタロウ");
        u.setPostalCode("123-4567");
        u.setAddress("東京都港区1-2-3");
        u.setPhoneNumber("0312345678");
        u.setEmail("host@example.com");
        // 役割が必要なら最低限
        Role r = new Role();
        r.setId(2);
        r.setName("ROLE_HOST");
        u.setRole(r);
        return u;
    }

    private UserDetailsImpl buildPrincipal(User u) {
        List<GrantedAuthority> auth = List.of(new SimpleGrantedAuthority("ROLE_HOST"));
        return new UserDetailsImpl(u, auth);
    }

    @Nested
    @DisplayName("GET /host")
    class Index {

        @Test
        @DisplayName("モデルにuserが入り、host/indexを返す")
        void ok() throws Exception {
            User u = buildUser();
            UserDetailsImpl principal = buildPrincipal(u);

            mvc.perform(get("/host").with(user(principal)))
               .andExpect(status().isOk())
               .andExpect(view().name("host/index"))
               .andExpect(model().attributeExists("user"));
        }
    }

    @Nested
    @DisplayName("GET /host/edit")
    class Edit {

        @Test
        @DisplayName("モデルにuserEditFormが入り、host/editを返す")
        void ok() throws Exception {
            User u = buildUser();
            UserDetailsImpl principal = buildPrincipal(u);

            mvc.perform(get("/host/edit").with(user(principal)))
               .andExpect(status().isOk())
               .andExpect(view().name("host/edit"))
               .andExpect(model().attributeExists("userEditForm"));
        }
    }

    @Nested
    @DisplayName("POST /host/update")
    class Update {

        @Test
        @DisplayName("メール変更かつ既登録 → フィールドエラーでhost/editに戻る")
        void emailConflict() throws Exception {
            User u = buildUser();
            UserDetailsImpl principal = buildPrincipal(u);

            // フォーム（メール変更）
            String newEmail = "dup@example.com";

            // サービスの振る舞いをスタブ
            given(userService.isEmailChanged(any(UserEditForm.class), eq(u))).willReturn(true);
            given(userService.isEmailRegistered(eq(newEmail))).willReturn(true);

            mvc.perform(post("/host/update")
                    .with(user(principal))
                    .with(csrf()) // ★ CSRF必須
                    .param("name", "山田太郎")
                    .param("furigana", "ヤマダタロウ")
                    .param("postalCode", "123-4567")
                    .param("address", "東京都港区1-2-3")
                    .param("phoneNumber", "0312345678")
                    .param("email", newEmail)
            )
            .andExpect(status().isOk())
            .andExpect(view().name("host/edit"))
            .andExpect(model().attributeHasFieldErrors("userEditForm", "email"));
        }

        @Test
        @DisplayName("正常更新 → /hostにリダイレクトしフラッシュメッセージ")
        void success() throws Exception {
            User u = buildUser();
            UserDetailsImpl principal = buildPrincipal(u);

            String sameEmail = u.getEmail();

            // 「メール未変更」ケース
            given(userService.isEmailChanged(any(UserEditForm.class), eq(u))).willReturn(false);

            mvc.perform(post("/host/update")
                    .with(user(principal))
                    .with(csrf())
                    .param("name", "山田太郎（更新）")
                    .param("furigana", "ヤマダタロウ")
                    .param("postalCode", "1234567")      // ★ ハイフン無し7桁に修正
                    .param("address", "東京都港区1-2-3")
                    .param("phoneNumber", "0312345678")  // ★ 数字のみ10〜11桁
                    .param("email", sameEmail)
            )
            // バリデーションOK → リダイレクト
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/host"))
            .andExpect(flash().attribute("successMessage", "会員情報を編集しました。"))
            // ついでにモデルエラーが無いことも確認しておくと安心
            .andExpect(model().hasNoErrors());

            // updateUserが呼ばれていること
            verify(userService).updateUser(any(UserEditForm.class), eq(u));
        }
    }
}
