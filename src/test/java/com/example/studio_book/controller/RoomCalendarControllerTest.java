// src/test/java/com/example/studio_book/controller/RoomCalendarControllerTest.java
package com.example.studio_book.controller;

// 追加
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.Closure;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.RoomBusinessHour;
import com.example.studio_book.repository.ClosureRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.repository.RoomBusinessHourRepository;

/**
 * RoomCalendarController の Web 層テスト
 *
 * ポイント:
 *  - start/end を OffsetDateTime(+09:00) で受け取り、コントローラ内で LocalDateTime へ変換していることの検証
 *  - [start, end) の排他端ループで日毎の OPEN 背景を作ること
 *  - 閉鎖(Closure)を差し引いて OPEN が分割される(subtract)こと
 *  - 予約(Reservation)が前面イベントとして返ること
 */
@WebMvcTest(RoomCalendarController.class)
@AutoConfigureMockMvc(addFilters = false)  // ★ SecurityFilterChain無効化
class RoomCalendarControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ClosureRepository closureRepo;

    @MockBean
    ReservationRepository reservationRepo;

    @MockBean
    RoomBusinessHourRepository bhRepo;

    private static final int ROOM_ID = 10;

    // --- テスト用ユーティリティ（必要最小限のフィールドだけセット） ---
    private RoomBusinessHour bh(int dow, boolean holiday, LocalTime start, LocalTime end) {
        RoomBusinessHour b = new RoomBusinessHour();
        // 1..7 (Mon..Sun) 前提の実装に合わせる
        b.setDayIndex(dow);
        b.setHoliday(holiday);
        b.setStartTime(start);
        b.setEndTime(end);
        return b;
    }

    private Closure closure(LocalDateTime s, LocalDateTime e) {
        Closure c = new Closure();
        c.setRoomId(ROOM_ID);
        c.setStartAt(s);
        c.setEndAt(e);
        return c;
    }

    private Reservation reservation(int id, LocalDateTime s, LocalDateTime e, String status) {
        Reservation r = new Reservation();
        r.setId(id);
        r.setStartAt(s);
        r.setEndAt(e);
        r.setStatus(status);

        // ★ Room を設定する（room_id を ManyToOne 経由で保持）
        Room room = new Room();
        room.setId(ROOM_ID);
        r.setRoom(room);

        return r;
    }
    private String isoOffset(LocalDateTime ldtJst) {
        // テストは JST(+09:00) で送る
        return OffsetDateTime.of(ldtJst, ZoneOffset.ofHours(9)).toString();
    }

    @Nested
    @DisplayName("/rooms/{roomId}/calendar/events")
    class Events {

        @Test
        @DisplayName("基本: 2日分の OPEN 背景が生成される（[start, end) の排他端確認）")
        void open_twoDays_basic() throws Exception {
            // 期間: 2025-11-10 00:00 ～ 2025-11-12 00:00 (JST) → 月・火の2日分
            var start = LocalDateTime.of(2025, 11, 10, 0, 0);
            var end   = LocalDateTime.of(2025, 11, 12, 0, 0);

            // 営業時間: 月(火) 10:00-18:00、祝日フラグなし
            given(bhRepo.findByRoomIdOrderByDayIndexAsc(ROOM_ID)).willReturn(List.of(
                bh(1, false, LocalTime.of(10, 0), LocalTime.of(18, 0)), // Mon
                bh(2, false, LocalTime.of(10, 0), LocalTime.of(18, 0))  // Tue
            ));

            // 閉鎖・予約なし
            given(closureRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of());
            given(reservationRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of());

            mvc.perform(get("/rooms/{roomId}/calendar/events", ROOM_ID)
                    .param("start", isoOffset(start))
                    .param("end",   isoOffset(end))
                    .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               // 2件（各日 10:00-18:00 の OPEN 背景）
               .andExpect(jsonPath("$.length()").value(2))
               .andExpect(jsonPath("$[0].display").value("background"))
               .andExpect(jsonPath("$[0].extendedProps.type").value("open"))
               .andExpect(jsonPath("$[1].extendedProps.type").value("open"))
               // ISO_LOCAL_DATE_TIME（オフセットなし）で返っていること
               .andExpect(jsonPath("$[0].start").value("2025-11-10T10:00:00"))
               .andExpect(jsonPath("$[0].end").value("2025-11-10T18:00:00"))
               .andExpect(jsonPath("$[1].start").value("2025-11-11T10:00:00"))
               .andExpect(jsonPath("$[1].end").value("2025-11-11T18:00:00"));

            // Repo 呼び出しの引数が LocalDateTime（JSTの壁を落とした値）で届いていることを確認
            ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> endCap   = ArgumentCaptor.forClass(LocalDateTime.class);
            then(closureRepo).should().findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), startCap.capture(), endCap.capture());
            // startCap.getValue() は 2025-11-10T00:00
            // endCap.getValue()   は 2025-11-12T00:00
            assert startCap.getValue().equals(start);
            assert endCap.getValue().equals(end);
        }

        @Test
        @DisplayName("閉鎖を差し引いて OPEN が分割される（10-13, 15-18）")
        void open_split_by_closure() throws Exception {
            var start = LocalDateTime.of(2025, 11, 10, 0, 0); // Mon
            var end   = LocalDateTime.of(2025, 11, 11, 0, 0); // [start, end) → 10日のみ

            given(bhRepo.findByRoomIdOrderByDayIndexAsc(ROOM_ID)).willReturn(List.of(
                bh(1, false, LocalTime.of(10, 0), LocalTime.of(18, 0)) // Mon
            ));

            // 10日 13:00-15:00 の閉鎖
            given(closureRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of(
                    closure(LocalDateTime.of(2025, 11, 10, 13, 0),
                            LocalDateTime.of(2025, 11, 10, 15, 0))
                ));

            given(reservationRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of());

            mvc.perform(get("/rooms/{roomId}/calendar/events", ROOM_ID)
                    .param("start", isoOffset(start))
                    .param("end",   isoOffset(end))
                    .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               // CLOSURE 背景イベント（色などは固定文字列）
               .andExpect(jsonPath("$[?(@.extendedProps.type=='closure')]", hasSize(1)));
        }

        @Test
        @DisplayName("予約は前面イベントとして返る（色・extendedProps 含む）")
        void reservation_foreground() throws Exception {
            var start = LocalDateTime.of(2025, 11, 10, 0, 0); // Mon
            var end   = LocalDateTime.of(2025, 11, 11, 0, 0);

            given(bhRepo.findByRoomIdOrderByDayIndexAsc(ROOM_ID)).willReturn(List.of(
                bh(1, false, LocalTime.of(10, 0), LocalTime.of(18, 0))
            ));

            given(closureRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of());

            given(reservationRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of(
                    reservation(9001, LocalDateTime.of(2025, 11, 10, 10, 0),
                                       LocalDateTime.of(2025, 11, 10, 12, 0), "PAID")
                ));

            mvc.perform(get("/rooms/{roomId}/calendar/events", ROOM_ID)
                    .param("start", isoOffset(start))
                    .param("end",   isoOffset(end))
                    .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               // 予約の方
               .andExpect(jsonPath("$[?(@.extendedProps.type=='reservation')]", hasSize(1)));
        }

        @Test
        @DisplayName("祝日（holiday=true）の曜日は OPEN を出さない")
        void holiday_skips_open() throws Exception {
            var start = LocalDateTime.of(2025, 11, 10, 0, 0); // Mon
            var end   = LocalDateTime.of(2025, 11, 12, 0, 0); // Mon, Tue

            // Mon が holiday=true, Tue は通常
            given(bhRepo.findByRoomIdOrderByDayIndexAsc(ROOM_ID)).willReturn(List.of(
                bh(1, true,  LocalTime.of(10, 0), LocalTime.of(18, 0)), // Mon holiday
                bh(2, false, LocalTime.of(10, 0), LocalTime.of(18, 0))  // Tue open
            ));

            given(closureRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of());
            given(reservationRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of());

            mvc.perform(get("/rooms/{roomId}/calendar/events", ROOM_ID)
                    .param("start", isoOffset(start))
                    .param("end",   isoOffset(end))
                    .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               // holiday の Mon は出ず、Tue の OPEN だけ 1件
               .andExpect(jsonPath("$.length()").value(1))
               .andExpect(jsonPath("$[0].extendedProps.type").value("open"))
               .andExpect(jsonPath("$[0].start").value("2025-11-11T10:00:00"))
               .andExpect(jsonPath("$[0].end").value("2025-11-11T18:00:00"));
        }

        @Test
        @DisplayName("営業時間の未設定（start/end null）はスキップされる")
        void no_time_skips_open() throws Exception {
            var start = LocalDateTime.of(2025, 11, 10, 0, 0); // Mon
            var end   = LocalDateTime.of(2025, 11, 11, 0, 0); // Mon only

            given(bhRepo.findByRoomIdOrderByDayIndexAsc(ROOM_ID)).willReturn(List.of(
                bh(1, false, null, null)
            ));

            given(closureRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of());
            given(reservationRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(eq(ROOM_ID), any(), any()))
                .willReturn(List.of());

            mvc.perform(get("/rooms/{roomId}/calendar/events", ROOM_ID)
                    .param("start", isoOffset(start))
                    .param("end",   isoOffset(end))
                    .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0)); // 何も出さない
        }
    }
}
