// src/test/java/com/example/studio_book/controller/PasswordResetControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.studio_book.entity.PasswordResetToken;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.PasswordResetTokenRepository;
import com.example.studio_book.repository.UserRepository;
import com.example.studio_book.service.PasswordResetService;

import jakarta.mail.MessagingException;

@WebMvcTest(controllers = PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false) // ★ SecurityFilterChain を無効化
class PasswordResetControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    PasswordResetService resetService;
    @MockBean
    PasswordResetTokenRepository tokenRepository;
    @MockBean
    UserRepository userRepository;
    @MockBean
    PasswordEncoder passwordEncoder;
    
    private static RequestPostProcessor csrfAttr() {
        return request -> {
            CsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "dummy");
            // Spring Security の規約キー（Class名）と Thymeleaf が参照する "_csrf" の両方を入れる
            request.setAttribute(CsrfToken.class.getName(), token);
            request.setAttribute("_csrf", token);
            return request;
        };
    }

    // --- /password/forgot ---

    @Test
    @DisplayName("GET /password/forgot: フォーム表示")
    void forgot_get_form() throws Exception {
        mvc.perform(get("/password/forgot").with(csrfAttr()))
           .andExpect(status().isOk())
           .andExpect(view().name("password/forgot"));
    }

    @Test
    @DisplayName("POST /password/forgot: メール送信成功 → 同じ画面に message")
    void forgot_post_success() throws Exception {
        String email = "user@example.com";
        willDoNothing().given(resetService).sendResetMail(email);

        mvc.perform(post("/password/forgot").with(csrf())
                .param("email", email))
           .andExpect(status().isOk())
           .andExpect(view().name("password/forgot"))
           .andExpect(model().attributeExists("message"));

        then(resetService).should().sendResetMail(email);
    }

    @Test
    @DisplayName("POST /password/forgot: 送信失敗（MessagingException）→ 同じ画面に error")
    void forgot_post_fail_sending() throws Exception {
        String email = "bad@example.com";
        willThrow(new MessagingException("oops")).given(resetService).sendResetMail(email);

        mvc.perform(post("/password/forgot").with(csrf())
                .param("email", email))
           .andExpect(status().isOk())
           .andExpect(view().name("password/forgot"))
           .andExpect(model().attributeExists("error"));
    }

    // --- /password/reset (GET) ---

    @Test
    @DisplayName("GET /password/reset: 無効トークン → forgot に error")
    void reset_get_invalid_token() throws Exception {
        given(tokenRepository.findByToken("nope")).willReturn(Optional.empty());

        mvc.perform(get("/password/reset").param("token", "nope").with(csrfAttr()))
           .andExpect(status().isOk())
           .andExpect(view().name("password/forgot"))
           .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("GET /password/reset: 期限切れ → forgot に error")
    void reset_get_expired_token() throws Exception {
        var expired = token("t1", "user@example.com", LocalDateTime.now().minusMinutes(1));
        given(tokenRepository.findByToken("t1")).willReturn(Optional.of(expired));

        mvc.perform(get("/password/reset").param("token", "t1").with(csrfAttr()))
           .andExpect(status().isOk())
           .andExpect(view().name("password/forgot"))
           .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("GET /password/reset: 有効トークン → reset 画面表示 + token 受け渡し")
    void reset_get_valid_token() throws Exception {
        var valid = token("t2", "user@example.com", LocalDateTime.now().plusMinutes(30));
        given(tokenRepository.findByToken("t2")).willReturn(Optional.of(valid));

        mvc.perform(get("/password/reset").param("token", "t2").with(csrfAttr()))
           .andExpect(status().isOk())
           .andExpect(view().name("password/reset"))
           .andExpect(model().attribute("token", "t2"));
    }

    // --- /password/reset (POST) ---

    @Test
    @DisplayName("POST /password/reset: 確認用パスワード不一致 → reset 画面に error + token保持")
    void reset_post_mismatch_confirm() throws Exception {
        mvc.perform(post("/password/reset").with(csrf())
                .param("token", "t")
                .param("password", "abc12345")
                .param("passwordConfirm", "xxx12345"))
           .andExpect(status().isOk())
           .andExpect(view().name("password/reset"))
           .andExpect(model().attributeExists("error"))
           .andExpect(model().attribute("token", "t"));

        then(tokenRepository).shouldHaveNoInteractions();
        then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /password/reset: 無効/期限切れトークン → forgot に error")
    void reset_post_invalid_or_expired() throws Exception {
        given(tokenRepository.findByToken("bad")).willReturn(Optional.empty());

        mvc.perform(post("/password/reset").with(csrf())
                .param("token", "bad")
                .param("password", "abc12345")
                .param("passwordConfirm", "abc12345"))
           .andExpect(status().isOk())
           .andExpect(view().name("password/forgot"))
           .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /password/reset: ユーザー未存在 → forgot に error")
    void reset_post_user_not_found() throws Exception {
        var valid = token("t3", "none@example.com", LocalDateTime.now().plusMinutes(10));
        given(tokenRepository.findByToken("t3")).willReturn(Optional.of(valid));
        given(userRepository.findByEmail("none@example.com")).willReturn(null);

        mvc.perform(post("/password/reset").with(csrf())
                .param("token", "t3")
                .param("password", "abc12345")
                .param("passwordConfirm", "abc12345"))
           .andExpect(status().isOk())
           .andExpect(view().name("password/forgot"))
           .andExpect(model().attributeExists("error"));

        then(tokenRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("POST /password/reset: 正常完了 → /login にリダイレクト + Flash(resetSuccess=true) + トークン削除")
    void reset_post_success() throws Exception {
        var valid = token("t4", "user@example.com", LocalDateTime.now().plusMinutes(30));
        given(tokenRepository.findByToken("t4")).willReturn(Optional.of(valid));

        User user = mock(User.class);
        given(userRepository.findByEmail("user@example.com")).willReturn(user);
        given(passwordEncoder.encode("abc12345")).willReturn("ENCODED");
        given(userRepository.save(user)).willReturn(user);

        var result = mvc.perform(post("/password/reset").with(csrf())
                .param("token", "t4")
                .param("password", "abc12345")
                .param("passwordConfirm", "abc12345"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/login"))
           .andExpect(flash().attribute("resetSuccess", true))
           .andReturn();

        // setPassword がエンコード済みで呼ばれていること
        then(user).should().setPassword("ENCODED");
        then(userRepository).should().save(user);

        // トークン再利用防止のため削除されること
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        then(tokenRepository).should().delete(captor.capture());
        // 念のため、削除対象が同じトークンかを確認
        // assertEquals("t4", captor.getValue().getToken()); // フィールドにゲッターがある場合だけ有効化
    }

    // ---- helper ----
    private PasswordResetToken token(String token, String email, LocalDateTime expiresAt) {
        // プロジェクトのエンティティに合わせて適宜フィールド名を調整してください
        PasswordResetToken prt = new PasswordResetToken();
        // 以下、セッターが無い場合はテスト用コンストラクタを追加するか、Builder を使ってください
        // 例: new PasswordResetToken(token, email, expiresAt)
        try {
            var cls = PasswordResetToken.class;
            // リフレクションで最低限の値をセット（セッターがあるなら普通に使ってOK）
            var f1 = cls.getDeclaredField("token");
            f1.setAccessible(true);
            f1.set(prt, token);
            var f2 = cls.getDeclaredField("email");
            f2.setAccessible(true);
            f2.set(prt, email);
            var f3 = cls.getDeclaredField("expiresAt");
            f3.setAccessible(true);
            f3.set(prt, expiresAt);
        } catch (Exception ignore) {}
        return prt;
    }
}
