// src/test/java/com/example/studio_book/controller/HostBusinessHourControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.form.BusinessHourRowForm;
import com.example.studio_book.form.BusinessHoursForm;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.BusinessHourService;
import com.example.studio_book.service.RoomService;

@WebMvcTest(controllers = HostBusinessHourController.class)
@AutoConfigureMockMvc
class HostBusinessHourControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    BusinessHourService businessHourService;

    @MockBean
    RoomService roomService;

    // ===== ヘルパ =====
    private UsernamePasswordAuthenticationToken auth() {
        User u = new User();
        u.setId(123);
        u.setEmail("host@example.com");
        u.setPassword("x");

        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_HOST"));

        var principal = new UserDetailsImpl(u, authorities);
        return new UsernamePasswordAuthenticationToken(principal, "pw", authorities);
    }

    private Room dummyRoom(int roomId) {
        Room r = new Room();
        r.setId(roomId);
        r.setName("ROOM-" + roomId);
        return r;
    }

    private BusinessHoursForm defaultForm(int roomId) {
        var rows = new ArrayList<BusinessHourRowForm>(7);
        for (int i = 1; i <= 7; i++) {
            rows.add(BusinessHourRowForm.builder()
                    .dayIndex(i)      // 1..7
                    .holiday(true)    // デフォルトは休み
                    .startTime(null)
                    .endTime(null)
                    .build());
        }
        return BusinessHoursForm.builder()
                .roomId(roomId)      // @NotNull 対応
                .rows(rows)
                .build();
    }

    // ====== GET ======
    @Test
    @DisplayName("GET /host/rooms/{roomId}/business-hours: 表示でき、modelに form/room/weekdayLabels")
    void get_edit_ok() throws Exception {
        int roomId = 10;

        // 1) スタブ
        willDoNothing().given(roomService).assertOwnedBy(eq(roomId), eq(123));
        given(roomService.findOwned(eq(roomId), eq(123))).willReturn(dummyRoom(roomId));
        given(businessHourService.loadOrDefault(eq(roomId))).willReturn(defaultForm(roomId));

        // 2) 実行
        mockMvc.perform(get("/host/rooms/{roomId}/business-hours", roomId)
                .with(authentication(auth())))
            .andExpect(status().isOk())
            .andExpect(view().name("host/rooms/business-hours/edit"))
            .andExpect(model().attributeExists("form"))
            .andExpect(model().attributeExists("room"))
            .andExpect(model().attributeExists("weekdayLabels"));

        // 3) 検証
        then(roomService).should().assertOwnedBy(roomId, 123);
        then(roomService).should(times(2)).findOwned(roomId, 123); // @ModelAttribute と edit() の2回
        then(businessHourService).should().loadOrDefault(roomId);
    }

    // ====== POST ======
    @Nested
    class PostUpdate {

        @Test
        @DisplayName("正常: 15分単位かつ開始<終了で保存→/host/rooms?success")
        void post_ok() throws Exception {
            int roomId = 10;

            // @ModelAttribute("room") 用
            given(roomService.findOwned(eq(roomId), eq(123))).willReturn(dummyRoom(roomId));
            willDoNothing().given(roomService).assertOwnedBy(eq(roomId), eq(123));
            willDoNothing().given(businessHourService).save(eq(roomId), any(BusinessHoursForm.class));

            mockMvc.perform(post("/host/rooms/{roomId}/business-hours", roomId)
                    .with(authentication(auth()))
                    .with(csrf())
                    .param("roomId", String.valueOf(roomId))
                    // 月のみ 09:00-18:00、他は休日
                    .param("rows[0].dayIndex", "1")
                    .param("rows[0].holiday", "false")
                    .param("rows[0].startTime", "09:00")
                    .param("rows[0].endTime", "18:00")
                    .param("rows[1].dayIndex", "2").param("rows[1].holiday", "true")
                    .param("rows[2].dayIndex", "3").param("rows[2].holiday", "true")
                    .param("rows[3].dayIndex", "4").param("rows[3].holiday", "true")
                    .param("rows[4].dayIndex", "5").param("rows[4].holiday", "true")
                    .param("rows[5].dayIndex", "6").param("rows[5].holiday", "true")
                    .param("rows[6].dayIndex", "7").param("rows[6].holiday", "true")
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/host/rooms?success"));

            then(roomService).should().assertOwnedBy(roomId, 123);
            then(roomService).should(times(1)).findOwned(roomId, 123); // POSTは @ModelAttribute 分のみ
            then(businessHourService).should().save(eq(roomId), any(BusinessHoursForm.class));
        }

        @Test
        @DisplayName("バリデーション: 必須（開始/終了がnull）でエラー")
        void post_required_errors() throws Exception {
            int roomId = 11;

            given(roomService.findOwned(eq(roomId), eq(123))).willReturn(dummyRoom(roomId));
            willDoNothing().given(roomService).assertOwnedBy(eq(roomId), eq(123));

            mockMvc.perform(post("/host/rooms/{roomId}/business-hours", roomId)
                    .with(authentication(auth()))
                    .with(csrf())
                    .param("roomId", String.valueOf(roomId))
                    // 月: holiday=false だが start/end 未入力 → 必須エラー
                    .param("rows[0].dayIndex", "1")
                    .param("rows[0].holiday", "false")
                    // 残りは休日
                    .param("rows[1].dayIndex", "2").param("rows[1].holiday", "true")
                    .param("rows[2].dayIndex", "3").param("rows[2].holiday", "true")
                    .param("rows[3].dayIndex", "4").param("rows[3].holiday", "true")
                    .param("rows[4].dayIndex", "5").param("rows[4].holiday", "true")
                    .param("rows[5].dayIndex", "6").param("rows[5].holiday", "true")
                    .param("rows[6].dayIndex", "7").param("rows[6].holiday", "true")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("host/rooms/business-hours/edit"))
            .andExpect(model().attributeHasFieldErrors("form", "rows[0].startTime"))
            .andExpect(model().attributeHasFieldErrors("form", "rows[0].endTime"));

            then(roomService).should().assertOwnedBy(roomId, 123);
            then(roomService).should(times(1)).findOwned(roomId, 123);
            then(businessHourService).should(never()).save(anyInt(), any());
        }

        @Test
        @DisplayName("バリデーション: 15分単位でない場合は time.step エラー")
        void post_step_errors() throws Exception {
            int roomId = 12;

            given(roomService.findOwned(eq(roomId), eq(123))).willReturn(dummyRoom(roomId));
            willDoNothing().given(roomService).assertOwnedBy(eq(roomId), eq(123));

            mockMvc.perform(post("/host/rooms/{roomId}/business-hours", roomId)
                    .with(authentication(auth()))
                    .with(csrf())
                    .param("roomId", String.valueOf(roomId))
                    // 火のみ 09:10-17:40 → 15分単位でない
                    .param("rows[0].dayIndex", "1").param("rows[0].holiday", "true")
                    .param("rows[1].dayIndex", "2").param("rows[1].holiday", "false")
                    .param("rows[1].startTime", "09:10")
                    .param("rows[1].endTime", "17:40")
                    .param("rows[2].dayIndex", "3").param("rows[2].holiday", "true")
                    .param("rows[3].dayIndex", "4").param("rows[3].holiday", "true")
                    .param("rows[4].dayIndex", "5").param("rows[4].holiday", "true")
                    .param("rows[5].dayIndex", "6").param("rows[5].holiday", "true")
                    .param("rows[6].dayIndex", "7").param("rows[6].holiday", "true")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("host/rooms/business-hours/edit"))
            .andExpect(model().attributeHasFieldErrors("form", "rows[1].startTime"))
            .andExpect(model().attributeHasFieldErrors("form", "rows[1].endTime"));

            then(roomService).should().assertOwnedBy(roomId, 123);
            then(roomService).should(times(1)).findOwned(roomId, 123);
            then(businessHourService).should(never()).save(anyInt(), any());
        }

        @Test
        @DisplayName("バリデーション: 終了が開始より後でない場合は time.order エラー")
        void post_order_error() throws Exception {
            int roomId = 13;

            given(roomService.findOwned(eq(roomId), eq(123))).willReturn(dummyRoom(roomId));
            willDoNothing().given(roomService).assertOwnedBy(eq(roomId), eq(123));

            mockMvc.perform(post("/host/rooms/{roomId}/business-hours", roomId)
                    .with(authentication(auth()))
                    .with(csrf())
                    .param("roomId", String.valueOf(roomId))
                    // 水のみ 10:00-10:00 → 後でない
                    .param("rows[0].dayIndex", "1").param("rows[0].holiday", "true")
                    .param("rows[1].dayIndex", "2").param("rows[1].holiday", "true")
                    .param("rows[2].dayIndex", "3").param("rows[2].holiday", "false")
                    .param("rows[2].startTime", "10:00")
                    .param("rows[2].endTime", "10:00")
                    .param("rows[3].dayIndex", "4").param("rows[3].holiday", "true")
                    .param("rows[4].dayIndex", "5").param("rows[4].holiday", "true")
                    .param("rows[5].dayIndex", "6").param("rows[5].holiday", "true")
                    .param("rows[6].dayIndex", "7").param("rows[6].holiday", "true")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("host/rooms/business-hours/edit"))
            .andExpect(model().attributeHasFieldErrors("form", "rows[2].endTime"));

            then(roomService).should().assertOwnedBy(roomId, 123);
            then(roomService).should(times(1)).findOwned(roomId, 123);
            then(businessHourService).should(never()).save(anyInt(), any());
        }

        @Test
        @DisplayName("休日行は時間未入力でもエラーにしない（holiday=true を優先）")
        void post_holiday_rows_ok_without_times() throws Exception {
            int roomId = 14;

            given(roomService.findOwned(eq(roomId), eq(123))).willReturn(dummyRoom(roomId));
            willDoNothing().given(roomService).assertOwnedBy(eq(roomId), eq(123));
            willDoNothing().given(businessHourService).save(eq(roomId), any(BusinessHoursForm.class));

            mockMvc.perform(post("/host/rooms/{roomId}/business-hours", roomId)
                    .with(authentication(auth()))
                    .with(csrf())
                    .param("roomId", String.valueOf(roomId))
                    // 全曜日 holiday=true（時間未入力）
                    .param("rows[0].dayIndex", "1").param("rows[0].holiday", "true")
                    .param("rows[1].dayIndex", "2").param("rows[1].holiday", "true")
                    .param("rows[2].dayIndex", "3").param("rows[2].holiday", "true")
                    .param("rows[3].dayIndex", "4").param("rows[3].holiday", "true")
                    .param("rows[4].dayIndex", "5").param("rows[4].holiday", "true")
                    .param("rows[5].dayIndex", "6").param("rows[5].holiday", "true")
                    .param("rows[6].dayIndex", "7").param("rows[6].holiday", "true")
            )
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/host/rooms?success"));

            then(roomService).should().assertOwnedBy(roomId, 123);
            then(roomService).should(times(1)).findOwned(roomId, 123);
            then(businessHourService).should().save(eq(roomId), any(BusinessHoursForm.class));
        }

        @Test
        @DisplayName("サービス層が IllegalArgumentException を投げたらビューに戻り error を表示")
        void post_service_illegal_argument() throws Exception {
            int roomId = 15;

            given(roomService.findOwned(eq(roomId), eq(123))).willReturn(dummyRoom(roomId));
            willDoNothing().given(roomService).assertOwnedBy(eq(roomId), eq(123));
            willThrow(new IllegalArgumentException("重複する時間帯があります"))
                .given(businessHourService).save(eq(roomId), any(BusinessHoursForm.class));

            mockMvc.perform(post("/host/rooms/{roomId}/business-hours", roomId)
                    .with(authentication(auth()))
                    .with(csrf())
                    .param("roomId", String.valueOf(roomId))
                    .param("rows[0].dayIndex", "1")
                    .param("rows[0].holiday", "false")
                    .param("rows[0].startTime", "09:00")
                    .param("rows[0].endTime", "12:00")
                    .param("rows[1].dayIndex", "2").param("rows[1].holiday", "true")
                    .param("rows[2].dayIndex", "3").param("rows[2].holiday", "true")
                    .param("rows[3].dayIndex", "4").param("rows[3].holiday", "true")
                    .param("rows[4].dayIndex", "5").param("rows[4].holiday", "true")
                    .param("rows[5].dayIndex", "6").param("rows[5].holiday", "true")
                    .param("rows[6].dayIndex", "7").param("rows[6].holiday", "true")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("host/rooms/business-hours/edit"))
            .andExpect(model().attributeExists("error"));

            then(roomService).should().assertOwnedBy(roomId, 123);
            then(roomService).should(times(1)).findOwned(roomId, 123);
            // save は should() で例外まで到達しているので追加検証は任意
        }
    }
}


