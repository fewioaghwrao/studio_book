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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.UserRepository;
import com.example.studio_book.service.RoomService;

@WebMvcTest(AdminRoomController.class)
@WithMockUser(username = "admin@example.com", roles = {"ADMIN"}) // ★ 管理者として実行
class AdminRoomControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RoomService roomService;

    @MockBean
    UserRepository userRepository;

    // ===== POJOヘルパ =====
    private Room newRoom(int id) {
        Room r = new Room();
        r.setId(id);
        r.setName("Test Room");
        r.setDescription("Nice room");
        r.setPrice(5000);     // Integer
        r.setCapacity(8);     // Integer
        r.setPostalCode("1000001");
        r.setAddress("東京都千代田区1-1-1");

        User host = new User();
        host.setId(99);
        r.setUser(host);
        return r;
    }

    // ===== index =====
    @Test
    @DisplayName("GET /admin/rooms | keywordなし: 全件ページング表示")
    void index_withoutKeyword() throws Exception {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Room> page = new PageImpl<>(List.of(newRoom(1)), pageable, 1);
        given(roomService.findAllRooms(any())).willReturn(page);

        mockMvc.perform(get("/admin/rooms"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/rooms/index"))
               .andExpect(model().attributeExists("roomPage"))
               .andExpect(model().attribute("keyword", (String) null));
    }

    @Test
    @DisplayName("GET /admin/rooms?keyword=abc | 名前LIKE検索")
    void index_withKeyword() throws Exception {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Room> page = new PageImpl<>(List.of(newRoom(2)), pageable, 1);
        given(roomService.findRoomByNameLike(eq("abc"), any())).willReturn(page);

        mockMvc.perform(get("/admin/rooms").param("keyword", "abc"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/rooms/index"))
               .andExpect(model().attributeExists("roomPage"))
               .andExpect(model().attribute("keyword", "abc"));
    }

    // ===== show =====
    @Test
    @DisplayName("GET /admin/rooms/{id} | 存在する -> show表示")
    void show_found() throws Exception {
        given(roomService.findRoomById(1)).willReturn(Optional.of(newRoom(1)));

        mockMvc.perform(get("/admin/rooms/1"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/rooms/show"))
               .andExpect(model().attributeExists("room"));
    }

    @Test
    @DisplayName("GET /admin/rooms/{id} | 不在 -> /admin/roomsへリダイレクト+エラー")
    void show_notFound() throws Exception {
        given(roomService.findRoomById(999)).willReturn(Optional.empty());

        mockMvc.perform(get("/admin/rooms/999"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/rooms"))
               .andExpect(flash().attribute("errorMessage", "スタジオが存在しません。"));
    }

    // ===== register (GET) =====
    @Test
    @DisplayName("GET /admin/rooms/register | フォーム+hosts設定")
    void register_get() throws Exception {
        given(userRepository.findAllHostsEnabled()).willReturn(List.of());

        mockMvc.perform(get("/admin/rooms/register"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/rooms/register"))
               .andExpect(model().attributeExists("roomRegisterForm"))
               .andExpect(model().attributeExists("hosts"));
    }

    // ===== create (POST) =====
    @Test
    @DisplayName("POST /admin/rooms/create | 入力エラー -> registerに戻る")
    void create_validationError() throws Exception {
        // バリデーションが有効な前提（NotBlank等）。無ければこのテストはスキップ。
        given(userRepository.findAllHostsEnabled()).willReturn(List.of());

        mockMvc.perform(post("/admin/rooms/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // 必須違反
                )
               .andExpect(status().isOk())
               .andExpect(view().name("admin/rooms/register"))
               .andExpect(model().attributeExists("roomRegisterForm"))
               .andExpect(model().attributeExists("hosts"))
               .andExpect(model().hasErrors());

        then(roomService).should(never()).createHouse(any());
    }

    @Test
    @DisplayName("POST /admin/rooms/create | 正常 -> /admin/roomsへリダイレクト+成功メッセージ")
    void create_success() throws Exception {
        willDoNothing().given(roomService).createHouse(any());

        mockMvc.perform(post("/admin/rooms/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Studio A")
                        .param("description", "Desc")
                        .param("price", "5000")
                        .param("capacity", "10")
                        .param("postalCode", "1000001")
                        .param("address", "東京都千代田区1-1-1")
                        .param("userId", "99")
                )
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/rooms"))
               .andExpect(flash().attribute("successMessage", "スタジオを登録しました。"));
    }

    // ===== edit (GET) =====
    @Test
    @DisplayName("GET /admin/rooms/{id}/edit | 存在 -> edit表示")
    void edit_found() throws Exception {
        given(roomService.findRoomById(10)).willReturn(Optional.of(newRoom(10)));
        given(userRepository.findAllHostsEnabled()).willReturn(List.of());

        mockMvc.perform(get("/admin/rooms/10/edit"))
               .andExpect(status().isOk())
               .andExpect(view().name("admin/rooms/edit"))
               .andExpect(model().attributeExists("room"))
               .andExpect(model().attributeExists("roomEditForm"))
               .andExpect(model().attributeExists("hosts"));
    }

    @Test
    @DisplayName("GET /admin/rooms/{id}/edit | 不在 -> /admin/roomsへリダイレクト+エラー")
    void edit_notFound() throws Exception {
        given(roomService.findRoomById(404)).willReturn(Optional.empty());

        mockMvc.perform(get("/admin/rooms/404/edit"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/rooms"))
               .andExpect(flash().attribute("errorMessage", "スタジオが存在しません。"));
    }

    // ===== update (POST) =====
    @Test
    @DisplayName("POST /admin/rooms/{id}/update | 入力エラー -> editへ戻る")
    void update_validationError() throws Exception {
        given(roomService.findRoomById(5)).willReturn(Optional.of(newRoom(5)));

        mockMvc.perform(post("/admin/rooms/5/update")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "") // 必須違反
                )
               .andExpect(status().isOk())
               .andExpect(view().name("admin/rooms/edit"))
               .andExpect(model().attributeExists("room"))
               .andExpect(model().attributeExists("roomEditForm"))
               .andExpect(model().hasErrors());

        then(roomService).should(never()).updateRoom(any(), any());
    }

    @Test
    @DisplayName("POST /admin/rooms/{id}/update | 正常 -> /admin/roomsへリダイレクト+成功メッセージ")
    void update_success() throws Exception {
        Room room = newRoom(6);
        given(roomService.findRoomById(6)).willReturn(Optional.of(room));
        willDoNothing().given(roomService).updateRoom(any(), eq(room));

        mockMvc.perform(post("/admin/rooms/6/update")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Updated")
                        .param("description", "NewDesc")
                        .param("price", "7000")
                        .param("capacity", "12")
                        .param("postalCode", "1000002")
                        .param("address", "東京都千代田区2-2-2")
                        .param("userId", "99")
                )
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/rooms"))
               .andExpect(flash().attribute("successMessage", "スタジオ情報を編集しました。"));
    }

    // ===== delete (POST) =====
    @Test
    @DisplayName("POST /admin/rooms/{id}/delete | 不在 -> /admin/roomsへリダイレクト+エラー")
    void delete_notFound() throws Exception {
        given(roomService.findRoomById(111)).willReturn(Optional.empty());

        mockMvc.perform(post("/admin/rooms/111/delete").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/rooms"))
               .andExpect(flash().attribute("errorMessage", "スタジオが存在しません。"));
    }

    @Test
    @DisplayName("POST /admin/rooms/{id}/delete | 正常 -> /admin/roomsへリダイレクト+成功メッセージ")
    void delete_success() throws Exception {
        Room room = newRoom(12);
        given(roomService.findRoomById(12)).willReturn(Optional.of(room));
        willDoNothing().given(roomService).deleteRoom(room);

        mockMvc.perform(post("/admin/rooms/12/delete").with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/rooms"))
               .andExpect(flash().attribute("successMessage", "スタジオを削除しました。"));
    }
}

