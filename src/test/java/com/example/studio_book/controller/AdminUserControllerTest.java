package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.studio_book.entity.Role;
import com.example.studio_book.entity.User;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.UserService;

@WebMvcTest(controllers = AdminUserController.class)
@AutoConfigureMockMvc(addFilters = true) // ★ フィルタONに変更
@Import({
    AdminUserControllerTest.SecurityTestConfig.class,
    AdminUserControllerTest.AuthPrincipalResolverConfig.class
})
class AdminUserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    UserService userService;
    
    @TestConfiguration
    static class SecurityTestConfig {
        @Bean
        org.springframework.security.web.SecurityFilterChain filterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {

            // 認可だけ最小化。CSRFはデフォルト有効のまま。
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

            return http.build();
        }
    }

    @TestConfiguration
    static class AuthPrincipalResolverConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(java.util.List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    // ------ ユーティリティ（テストデータ作成） ------

    private User user(int id, String name) {
        User u = new User();
        u.setId(id);
        u.setName(name);
        // コントローラは target.getRole() を参照するので最低限セット
        Role r = new Role();
        r.setId(2); // 一般ユーザ想定（管理者=3 という前提に合わせる）
        r.setName("ROLE_USER");
        u.setRole(r);
        u.setEnabled(true);
        return u;
    }

    private User adminUser(int id, String name) {
        User u = user(id, name);
        u.getRole().setId(3);
        u.getRole().setName("ROLE_ADMIN");
        u.setEmail("admin@example.com"); // ★ getUsername()=email対策（念のため）
        return u;
    }

    private UsernamePasswordAuthenticationToken auth(User actor) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var principal = new UserDetailsImpl(actor, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        return new UsernamePasswordAuthenticationToken(principal, "N/A", authorities);
    }

    // ------------------ 一覧 ------------------

    @Test
    @DisplayName("GET /admin/users：キーワード無し→findAllUsers が呼ばれ一覧を返す")
    void index_withoutKeyword() throws Exception {
        Page<User> page = new PageImpl<>(List.of(user(1, "Taro")));
        given(userService.findAllUsers(any())).willReturn(page);

        mockMvc.perform(get("/admin/users").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/users/index"));

        then(userService).should().findAllUsers(any());
        then(userService).should(never()).findUsersByNameLikeOrFuriganaLike(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("GET /admin/users?keyword=太郎：キーワード有り→like検索が呼ばれる")
    void index_withKeyword() throws Exception {
        Page<User> page = new PageImpl<>(List.of(user(2, "太郎")));
        given(userService.findUsersByNameLikeOrFuriganaLike(eq("太郎"), eq("太郎"), any()))
            .willReturn(page);

        mockMvc.perform(get("/admin/users").param("keyword", "太郎").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/users/index"));

        then(userService).should().findUsersByNameLikeOrFuriganaLike(eq("太郎"), eq("太郎"), any());
        then(userService).should(never()).findAllUsers(any());
    }

    // ------------------ 詳細 ------------------

    @Test
    @DisplayName("GET /admin/users/{id}：存在する→show ビューと user モデル")
    void show_exists() throws Exception {
        var u = user(10, "Hanako");
        given(userService.findUserById(10)).willReturn(Optional.of(u));

        mockMvc.perform(get("/admin/users/10"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/users/show"))
               .andExpect(model().attributeExists("user"));

        then(userService).should().findUserById(10);
    }

    @Test
    @DisplayName("GET /admin/users/{id}：存在しない→一覧へリダイレクト＆フラッシュメッセージ")
    void show_notFound() throws Exception {
        given(userService.findUserById(99)).willReturn(Optional.empty());

        mockMvc.perform(get("/admin/users/99"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/users"))
               .andExpect(flash().attributeExists("errorMessage"));

        then(userService).should().findUserById(99);
    }

    // -------------- 有効/無効 更新 --------------

    @Test
    @DisplayName("POST /admin/users/{id}/enabled：成功（actor あり）→成功メッセージで一覧へ")
    void updateEnabled_success_withActor() throws Exception {
        var target = user(20, "User20");
        given(userService.findUserById(20)).willReturn(Optional.of(target));

        // actor(User) と それを包む UserDetailsImpl を用意
        var actor = adminUser(1, "Admin");
        List<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var principal = new UserDetailsImpl(actor, authorities);

        // 期待：第三引数 actorId=1
        willDoNothing().given(userService).setEnabled(eq(target), eq(false), eq(1));

        mockMvc.perform(post("/admin/users/20/enabled")
                .param("enabled", "false")
                .with(csrf())
                // ここが重要：Principal を UserDetailsImpl にする
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(principal)))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/users"))
            .andExpect(flash().attributeExists("successMessage"));

        then(userService).should().setEnabled(eq(target), eq(false), eq(1));
    }

    @Test
    @DisplayName("POST /admin/users/{id}/enabled：対象が管理者→更新せずエラーで一覧へ")
    void updateEnabled_targetIsAdmin_forbidden() throws Exception {
        var admin = adminUser(30, "AdminTarget");
        given(userService.findUserById(30)).willReturn(Optional.of(admin));

        mockMvc.perform(post("/admin/users/30/enabled")
                        .param("enabled", "true")
                        .with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/users"))
               .andExpect(flash().attributeExists("errorMessage"));

        // setEnabled は呼ばれない
        then(userService).should(never()).setEnabled(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("POST /admin/users/{id}/enabled：対象が存在しない→エラーで一覧へ")
    void updateEnabled_notFound() throws Exception {
        given(userService.findUserById(404)).willReturn(Optional.empty());

        mockMvc.perform(post("/admin/users/404/enabled")
                        .param("enabled", "true")
                        .with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/users"))
               .andExpect(flash().attributeExists("errorMessage"));

        then(userService).should().findUserById(404);
        then(userService).should(never()).setEnabled(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("POST /admin/users/{id}/enabled：更新時に例外→エラーフラッシュで一覧へ")
    void updateEnabled_serviceThrows() throws Exception {
        var target = user(50, "User50");
        given(userService.findUserById(50)).willReturn(Optional.of(target));
        willThrow(new RuntimeException("boom")).given(userService)
                .setEnabled(eq(target), eq(true), isNull());

        // principal を渡さないケース（@AuthenticationPrincipal が null → actorId=null）
        mockMvc.perform(post("/admin/users/50/enabled")
                        .param("enabled", "true")
                        .with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/users"))
               .andExpect(flash().attributeExists("errorMessage"));

        then(userService).should().setEnabled(eq(target), eq(true), isNull());
    }
}
