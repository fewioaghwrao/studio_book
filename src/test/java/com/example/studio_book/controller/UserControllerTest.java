// src/test/java/com/example/studio_book/controller/UserControllerTest.java
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.User;
import com.example.studio_book.form.UserEditForm;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.UserService;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    UserService userService;

    // ---- helpers ----

    private Authentication authWith(User principalUser) {
        UserDetailsImpl principal = mock(UserDetailsImpl.class, RETURNS_DEEP_STUBS);
        when(principal.getUser()).thenReturn(principalUser);

        var authorities = List.of(new SimpleGrantedAuthority("ROLE_GENERAL"));
        return new UsernamePasswordAuthenticationToken(principal, "pw", authorities);
    }
    private User sampleUser() {
        User u = new User();
        // プロジェクトの User に合わせて必要な項目をセットしてください
        u.setId(10);
        u.setName("山田太郎");
        u.setFurigana("ヤマダタロウ");
        u.setPostalCode("1000001");
        u.setAddress("東京都千代田区1-1-1");
        u.setPhoneNumber("0312345678");
        u.setEmail("taro@example.com");
        return u;
    }

    @Nested
    @DisplayName("GET /user")
    class Index {

        @Test
        @DisplayName("本人情報を表示できる（modelにuserあり）")
        void index_success() throws Exception {
            var user = sampleUser();

            mvc.perform(get("/user")
                    .with(authentication(authWith(user))))
               .andExpect(status().isOk())
               .andExpect(view().name("user/index"))
               .andExpect(model().attributeExists("user"));
        }
    }

    @Nested
    @DisplayName("GET /user/edit")
    class Edit {

        @Test
        @DisplayName("編集フォームを初期表示できる（modelにuserEditFormあり）")
        void edit_success() throws Exception {
            var user = sampleUser();

            mvc.perform(get("/user/edit")
                    .with(authentication(authWith(user))))
               .andExpect(status().isOk())
               .andExpect(view().name("user/edit"))
               .andExpect(model().attributeExists("userEditForm"));
        }
    }

    @Nested
    @DisplayName("POST /user/update")
    class Update {

        @Test
        @DisplayName("正常系：更新して /user にリダイレクト & フラッシュメッセージ")
        void update_success_redirect() throws Exception {
            var user = sampleUser();

            // メールアドレス変更/重複チェックは false 側に倒して通す
            given(userService.isEmailChanged(any(UserEditForm.class), same(user))).willReturn(false);

            mvc.perform(post("/user/update")
                    .with(authentication(authWith(user)))
                    .with(csrf())
                    .param("name", "山田太郎")
                    .param("furigana", "ヤマダタロウ")
                    .param("postalCode", "1000001")
                    .param("address", "東京都千代田区1-1-1")
                    .param("phoneNumber", "0312345678")
                    .param("email", "taro_new@example.com"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/user"))
               .andExpect(flash().attribute("successMessage", "会員情報を編集しました。"));

            then(userService).should().updateUser(any(UserEditForm.class), same(user));
        }

        @Test
        @DisplayName("メール重複：isEmailChanged=true かつ isEmailRegistered=true → user/edit に戻り、emailにフィールドエラー")
        void update_duplicate_email_error() throws Exception {
            var user = sampleUser();

            given(userService.isEmailChanged(any(UserEditForm.class), same(user))).willReturn(true);
            given(userService.isEmailRegistered(eq("dup@example.com"))).willReturn(true);

            mvc.perform(post("/user/update")
            		 .with(authentication(authWith(user)))
                    .with(csrf())
                    .param("name", "山田太郎")
                    .param("furigana", "ヤマダタロウ")
                    .param("postalCode", "1000001")
                    .param("address", "東京都千代田区1-1-1")
                    .param("phoneNumber", "0312345678")
                    .param("email", "dup@example.com"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/edit"))
               .andExpect(model().attributeExists("userEditForm"))
               .andExpect(model().attributeHasFieldErrors("userEditForm", "email"));

            then(userService).should(never()).updateUser(any(), any());
        }

        @Test
        @DisplayName("バリデーションエラー例：メール形式不正 → user/edit に戻り、emailフィールドエラー")
        void update_validation_error_email_format() throws Exception {
            var user = sampleUser();

            // サービス側は false に倒して通す
            given(userService.isEmailChanged(any(UserEditForm.class), same(user))).willReturn(false);

            mvc.perform(post("/user/update")
                    .with(authentication(authWith(user)))   // ← ここを必ず authentication(...) に
                    .with(csrf())
                    .param("name", "山田太郎")
                    .param("furigana", "ヤマダタロウ")
                    .param("postalCode", "1000001")
                    .param("address", "東京都千代田区1-1-1")
                    .param("phoneNumber", "0312345678")
                    .param("email", "invalid-email"))
               .andExpect(status().isOk())
               .andExpect(view().name("user/edit"))
               .andExpect(model().attributeExists("userEditForm"))
               .andExpect(model().attributeHasFieldErrors("userEditForm", "email"));

            then(userService).should(never()).updateUser(any(), any());
        }
    }
}

