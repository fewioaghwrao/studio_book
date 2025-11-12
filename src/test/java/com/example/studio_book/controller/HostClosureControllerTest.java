// src/test/java/com/example/studio_book/controller/HostClosureControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.Closure;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.ClosureService;

@WebMvcTest(controllers = HostClosureController.class)
@AutoConfigureMockMvc(addFilters = false)
class HostClosureControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ClosureService closureService;

    /** Principal 生成 */
    private UserDetailsImpl principal() {
        User u = new User();
        u.setId(100);
        u.setEmail("host@example.com");
        u.setPassword("{noop}pass");
        return new UserDetailsImpl(
            u,
            List.of(new SimpleGrantedAuthority("ROLE_HOST"))
        );
    }

    /** index表示 */
    @Test
    @DisplayName("GET /host/rooms/{roomId}/closures: モデル・ビューを返す")
    void index_returnsViewAndModel() throws Exception {
        int roomId = 10;
        Room room = new Room(); room.setId(roomId); room.setName("Room A");

        Closure c = new Closure();
        c.setId(1); c.setRoomId(roomId);
        c.setStartAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        c.setEndAt(LocalDateTime.of(2025, 1, 2, 0, 0));
        c.setReason("テスト");

        given(closureService.getOwnedRoomOrThrow(eq(roomId), any())).willReturn(room);
        given(closureService.list(eq(roomId), any())).willReturn(List.of(c));

        var csrf = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "dummy-token");

        mockMvc.perform(get("/host/rooms/{roomId}/closures", roomId)
                .with(user(principal()))
                .requestAttr("_csrf", csrf))                 // ← これを追加
            .andExpect(status().isOk())
            .andExpect(view().name("host/closures/index"))
            .andExpect(model().attributeExists("room", "closures", "closureForm"));
    }

    /** events JSON */
    @Test
    @DisplayName("GET /host/rooms/{roomId}/closures/events -> JSON")
    void events() throws Exception {
        int roomId = 10;

        Closure allDay = new Closure();
        allDay.setId(1);
        allDay.setRoomId(roomId);
        allDay.setStartAt(LocalDateTime.of(2025, 12, 1, 0, 0));
        allDay.setEndAt(LocalDateTime.of(2025, 12, 2, 0, 0));
        allDay.setReason("");

        Closure timed = new Closure();
        timed.setId(2);
        timed.setRoomId(roomId);
        timed.setStartAt(LocalDateTime.of(2025, 12, 5, 9, 0));
        timed.setEndAt(LocalDateTime.of(2025, 12, 5, 18, 0));
        timed.setReason("設備点検");

        given(closureService.list(eq(roomId), any()))
            .willReturn(List.of(allDay, timed));

        mockMvc.perform(
                get("/host/rooms/{roomId}/closures/events", roomId)
                .with(user(principal()))
                .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].title").value("休館"))
            .andExpect(jsonPath("$[0].allDay").value(true))
            .andExpect(jsonPath("$[1].id").value(2))
            .andExpect(jsonPath("$[1].title").value("休館: 設備点検"))
            .andExpect(jsonPath("$[1].allDay").value(false));

        then(closureService).should().list(eq(roomId), any());
    }

    @Nested
    @DisplayName("POST /host/rooms/{roomId}/closures")
    class CreateTests {

        @Test
        @DisplayName("正常系 -> リダイレクト")
        void create_success() throws Exception {
            int roomId = 10;

            mockMvc.perform(
                    post("/host/rooms/{roomId}/closures", roomId)
                    .with(user(principal()))
                    .with(csrf())
                    .param("startAt", "2025-12-01T00:00")
                    .param("endAt",   "2025-12-02T00:00")
                    .param("reason",  "年末清掃")
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/host/rooms/10/closures?success=1"));

            then(closureService).should().create(
                eq(roomId),
                eq(LocalDateTime.parse("2025-12-01T00:00")),
                eq(LocalDateTime.parse("2025-12-02T00:00")),
                eq("年末清掃"),
                any()
            );
        }

        @Test
        @DisplayName("バリデーション NG -> index再表示")
        void create_validationError() throws Exception {
            int roomId = 10;

            Room room = new Room();
            room.setId(roomId);

            given(closureService.getOwnedRoomOrThrow(eq(roomId), any())).willReturn(room);
            given(closureService.list(eq(roomId), any())).willReturn(List.of());

            mockMvc.perform(
                    post("/host/rooms/{roomId}/closures", roomId)
                    .with(user(principal()))
                    .with(csrf())
                    .param("reason", "理由だけ")  // startAt,endAt無し
                )
                .andExpect(status().isOk())
                .andExpect(view().name("host/closures/index"))
                .andExpect(model().attributeExists("room"))
                .andExpect(model().attributeExists("closures"))
                .andExpect(model().attributeExists("closureForm"));

            then(closureService).should(never())
                .create(anyInt(), any(), any(), anyString(), any());
        }

        @Test
        @DisplayName("サービス例外 -> エラー付き再表示")
        void create_exception() throws Exception {
            int roomId = 10;

            Room room = new Room();
            room.setId(roomId);

            given(closureService.getOwnedRoomOrThrow(eq(roomId), any())).willReturn(room);
            given(closureService.list(eq(roomId), any())).willReturn(List.of());

            willThrow(new IllegalStateException("重複"))
                .given(closureService)
                .create(eq(roomId), any(), any(), anyString(), any());

            mockMvc.perform(
                    post("/host/rooms/{roomId}/closures", roomId)
                    .with(user(principal()))
                    .with(csrf())
                    .param("startAt", "2025-12-01T00:00")
                    .param("endAt",   "2025-12-02T00:00")
                    .param("reason",  "test")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("host/closures/index"))
                .andExpect(model().attributeExists("error"));
        }
    }

    @Test
    @DisplayName("POST /{closureId}/delete -> リダイレクト")
    void delete() throws Exception {
        int roomId = 10;
        int closureId = 99;

        mockMvc.perform(
                post("/host/rooms/{roomId}/closures/{closureId}/delete", roomId, closureId)
                .with(user(principal()))
                .with(csrf())
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/host/rooms/10/closures?success=1"));

        then(closureService).should()
            .delete(eq(roomId), eq(closureId), any());
    }
}

