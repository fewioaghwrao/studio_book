// src/test/java/com/example/studio_book/controller/AdminProfileControllerTest.java
package com.example.studio_book.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.User;
import com.example.studio_book.form.UserEditForm;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.UserService;

@WebMvcTest(AdminProfileController.class)
class AdminProfileControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    UserService userService;

    private User currentUser;
    private UserDetailsImpl principal;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(100);
        currentUser.setName("管理者 太郎");
        currentUser.setFurigana("カンリシャ タロウ");
        currentUser.setPostalCode("4600001");
        currentUser.setAddress("愛知県名古屋市中区1-1-1");
        currentUser.setPhoneNumber("052-000-0000");
        currentUser.setEmail("admin@example.com");
        // Securityで必要なので有効化
        currentUser.setEnabled(true); 

        // ✅ authorities を必ず入れる
        principal = new UserDetailsImpl(
                currentUser,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    void GET_profile_index_ユーザ情報をmodelにセットしてビューを返す() throws Exception {
        mvc.perform(get("/admin/profile").with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/profile/index"))
            .andExpect(model().attributeExists("user"))
            .andExpect(model().attribute("user", hasProperty("id", is(currentUser.getId()))));
    }

    @Test
    void GET_profile_edit_UserEditFormに初期値が入る() throws Exception {
        mvc.perform(get("/admin/profile/edit").with(user(principal)))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/profile/edit"))
            .andExpect(model().attributeExists("userEditForm"))
            .andExpect(model().attribute("userEditForm", allOf(
                    hasProperty("name", is(currentUser.getName())),
                    hasProperty("furigana", is(currentUser.getFurigana())),
                    hasProperty("postalCode", is(currentUser.getPostalCode())),
                    hasProperty("address", is(currentUser.getAddress())),
                    hasProperty("phoneNumber", is(currentUser.getPhoneNumber())),
                    hasProperty("email", is(currentUser.getEmail()))
            )));
    }

    @Test
    void POST_update_メール重複でエラーしeditへ戻る() throws Exception {
        when(userService.isEmailChanged(any(UserEditForm.class), eq(currentUser))).thenReturn(true);
        when(userService.isEmailRegistered("dup@example.com")).thenReturn(true);

        mvc.perform(post("/admin/profile/update")
                .with(user(principal))
                .with(csrf())
                .param("name", "管理者 太郎")
                .param("furigana", "カンリシャ タロウ")
                .param("postalCode", "4600001")
                .param("address", "愛知県名古屋市中区1-1-1")
                .param("phoneNumber", "052-000-0000")
                .param("email", "dup@example.com"))
            .andExpect(status().isOk())
            .andExpect(view().name("admin/profile/edit"))
            .andExpect(model().attributeHasFieldErrors("userEditForm", "email"));

        verify(userService, never()).updateUser(any(UserEditForm.class), any(User.class));
    }

    @Test
    void POST_update_メール変更あり_重複なし_成功() throws Exception {
        when(userService.isEmailChanged(any(UserEditForm.class), eq(currentUser))).thenReturn(true);
        when(userService.isEmailRegistered("new@example.com")).thenReturn(false);

        mvc.perform(post("/admin/profile/update")
                .with(user(principal))
                .with(csrf())
                .param("name", "管理者 次郎")
                .param("furigana", "カンリシャジロウ")  // ★ スペース無し
                .param("postalCode", "4600002")
                .param("address", "愛知県名古屋市中区2-2-2")
                .param("phoneNumber", "0521111111")    // ★ 数字のみ
                .param("email", "new@example.com"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/profile"))
            .andExpect(flash().attribute("successMessage", "会員情報を編集しました。"))
            .andExpect(model().hasNoErrors());

        verify(userService).updateUser(any(UserEditForm.class), eq(currentUser));
    }

    @Test
    void POST_update_メール未変更なら重複チェックせず成功() throws Exception {
        when(userService.isEmailChanged(any(UserEditForm.class), eq(currentUser))).thenReturn(false);

        mvc.perform(post("/admin/profile/update")
                .with(user(principal))
                .with(csrf())
                .param("name", "管理者 太郎(修正)")
                // ★ フリガナ：スペース抜き（制約が “全角カナのみ” なら）
                .param("furigana", "カンリシャタロウ")
                // ★ 郵便番号：7桁（あなたのフォームがハイフン無し7桁ならこのままOK）
                .param("postalCode", "4600001")
                .param("address", "愛知県名古屋市中区1-1-1")
                // ★ 電話番号：数字のみ 10〜11桁（ハイフン削除）
                .param("phoneNumber", "0520000000")
                .param("email", "admin@example.com"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/profile"))
            .andExpect(flash().attribute("successMessage", "会員情報を編集しました。"))
            // ついでに「モデルにエラーが無い」ことも固定化
            .andExpect(model().hasNoErrors());

        verify(userService, never()).isEmailRegistered(anyString());
        verify(userService).updateUser(any(UserEditForm.class), eq(currentUser));
    }
}

