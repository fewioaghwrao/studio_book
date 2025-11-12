// src/test/java/com/example/studio_book/controller/AuthControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.User;
import com.example.studio_book.entity.VerificationToken;
import com.example.studio_book.event.SignupEventPublisher;
import com.example.studio_book.service.UserService;
import com.example.studio_book.service.VerificationTokenService;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Securityフィルタ無効（401回避）。有効にするなら `.with(csrf())` は不要
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserService userService;

    @MockBean
    SignupEventPublisher signupEventPublisher;

    @MockBean
    VerificationTokenService verificationTokenService;

    // 成功ケースで使う妥当な入力（あなたの制約に合わせて値を用意）
    private Map<String, String> validParams() {
        return Map.of(
                "name", "山田太郎",
                "furigana", "ヤマダタロウ",
                "postalCode", "4600000",
                "address", "愛知県名古屋市中区1-2-3",
                "phoneNumber", "0520000000",
                "email", "ok@example.com",
                "password", "Secret123!",
                "passwordConfirmation", "Secret123!",
                "roleId", "1" // 1〜2 の範囲
        );
    }

    @Test
    @DisplayName("GET /login: CSRF属性が無いテンプレ事故を避ける（_csrf付きで呼ぶ）")
    void getLogin_returnsView() throws Exception {
        mockMvc.perform(get("/login").with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("auth/login"));
    }

    @Test
    @DisplayName("GET /signup: 画面表示とsignupForm")
    void getSignup_returnsViewAndModel() throws Exception {
        mockMvc.perform(get("/signup").with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("auth/signup"))
               .andExpect(model().attributeExists("signupForm"));
    }

    @Nested
    class PostSignup {

        @Test
        @DisplayName("POST /signup: 既登録メールは email フィールドエラーで画面戻り")
        void postSignup_emailAlreadyRegistered_returnsError() throws Exception {
            when(userService.isEmailRegistered("dup@example.com")).thenReturn(true);
            when(userService.isSamePassword(anyString(), anyString())).thenReturn(true);

            mockMvc.perform(post("/signup").with(csrf())
                            .param("name", "山田太郎")
                            .param("furigana", "ヤマダタロウ")
                            .param("postalCode", "4600000")
                            .param("address", "愛知県名古屋市中区1-2-3")
                            .param("phoneNumber", "0520000000")
                            .param("email", "dup@example.com")
                            .param("password", "Secret123!")
                            .param("passwordConfirmation", "Secret123!")
                            .param("roleId", "2"))
                   .andExpect(status().isOk())
                   .andExpect(view().name("auth/signup"))
                   .andExpect(model().attributeHasFieldErrors("signupForm", "email"));
        }

        @Test
        @DisplayName("POST /signup: パスワード不一致は password フィールドエラーで画面戻り")
        void postSignup_passwordMismatch_returnsError() throws Exception {
            when(userService.isEmailRegistered("new@example.com")).thenReturn(false);
            when(userService.isSamePassword("Secret123!", "Different!")).thenReturn(false);

            mockMvc.perform(post("/signup").with(csrf())
                            .param("name", "山田太郎")
                            .param("furigana", "ヤマダタロウ")
                            .param("postalCode", "4600000")
                            .param("address", "愛知県名古屋市中区1-2-3")
                            .param("phoneNumber", "0520000000")
                            .param("email", "new@example.com")
                            .param("password", "Secret123!")
                            .param("passwordConfirmation", "Different!")
                            .param("roleId", "1"))
                   .andExpect(status().isOk())
                   .andExpect(view().name("auth/signup"))
                   .andExpect(model().attributeHasFieldErrors("signupForm", "password"));
        }

        @Test
        @DisplayName("POST /signup: 正常系（全必須OK → 作成 → イベント発行 → フラッシュ → /へリダイレクト）")
        void postSignup_success_redirectsWithFlash() throws Exception {
            when(userService.isEmailRegistered("ok@example.com")).thenReturn(false);
            when(userService.isSamePassword("Secret123!", "Secret123!")).thenReturn(true);

            User created = new User();
            created.setId(100);
            created.setEmail("ok@example.com");
            when(userService.createUser(any())).thenReturn(created);

            var req = post("/signup").with(csrf());
            validParams().forEach((k, v) -> req.param(k, v));

            var result = mockMvc.perform(req)
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/"))
                    .andExpect(flash().attributeExists("successMessage"))
                    .andReturn();

            // イベント引数検証
            ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
            ArgumentCaptor<String> urlCap = ArgumentCaptor.forClass(String.class);
            verify(signupEventPublisher).publishSignupEvent(userCap.capture(), urlCap.capture());

            assert userCap.getValue().getId() == 100;
            assert urlCap.getValue() != null && urlCap.getValue().startsWith("http");
        }
    }

    @Nested
    class GetVerify {
        @Test
        @DisplayName("GET /signup/verify: トークン有効 → enableUser と成功メッセージ")
        void verify_validToken() throws Exception {
            User user = new User();
            user.setId(200);
            VerificationToken token = new VerificationToken();
            token.setUser(user);
            when(verificationTokenService.getVerificationToken("VALID")).thenReturn(token);

            mockMvc.perform(get("/signup/verify").with(csrf()).param("token", "VALID"))
                   .andExpect(status().isOk())
                   .andExpect(view().name("auth/verify"))
                   .andExpect(model().attributeExists("successMessage"))
                   .andExpect(model().attributeDoesNotExist("errorMessage"));

            verify(userService).enableUser(user);
        }

        @Test
        @DisplayName("GET /signup/verify: トークン無効 → エラーメッセージのみ")
        void verify_invalidToken() throws Exception {
            when(verificationTokenService.getVerificationToken("BAD")).thenReturn(null);

            mockMvc.perform(get("/signup/verify").with(csrf()).param("token", "BAD"))
                   .andExpect(status().isOk())
                   .andExpect(view().name("auth/verify"))
                   .andExpect(model().attributeExists("errorMessage"))
                   .andExpect(model().attributeDoesNotExist("successMessage"));

            verify(userService, never()).enableUser(any());
        }
    }
}


