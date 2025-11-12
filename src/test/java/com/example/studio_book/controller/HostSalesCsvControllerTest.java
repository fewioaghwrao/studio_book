// src/test/java/com/example/studio_book/controller/HostSalesCsvControllerTest.java
package com.example.studio_book.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.dto.HostSalesHead;
import com.example.studio_book.dto.HostSalesRowProjection;
import com.example.studio_book.entity.ReservationChargeItem;
import com.example.studio_book.entity.Role;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.ReservationChargeItemRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.security.UserDetailsImpl;

import jakarta.servlet.ServletException;

@WebMvcTest(controllers = HostSalesCsvController.class)
@AutoConfigureMockMvc
class HostSalesCsvControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ReservationRepository reservationRepository;

    @MockBean
    ReservationChargeItemRepository chargeItemRepository;

    // ===== helpers =====
    private UserDetailsImpl detailsOf(int hostId) {
        User u = new User();
        u.setId(hostId);
        Role r = new Role();
        r.setName("ROLE_HOST");
        u.setRole(r);
        return new UserDetailsImpl(u, List.of(new SimpleGrantedAuthority("ROLE_HOST")));
    }

    private HostSalesRowProjection listRow(int reservationId, int roomId, String room, String guest,
                                           LocalDateTime start, LocalDateTime end,
                                           int amount, String status) {
        HostSalesRowProjection p = Mockito.mock(HostSalesRowProjection.class);
        // インタフェースの getter 名に合わせてスタブ
        given(p.getReservationId()).willReturn(reservationId);
        given(p.getRoomId()).willReturn(roomId);
        given(p.getRoomName()).willReturn(room);
        given(p.getGuestName()).willReturn(guest);
        given(p.getStartAt()).willReturn(start);
        given(p.getEndAt()).willReturn(end);
        given(p.getAmount()).willReturn(amount);
        given(p.getStatus()).willReturn(status);
        return p;
    }

    private HostSalesHead head(int reservationId, String room, String guest,
                               LocalDateTime start, LocalDateTime end,
                               int amount, String status) {
        HostSalesHead h = Mockito.mock(HostSalesHead.class);
        given(h.getReservationId()).willReturn(reservationId);
        given(h.getRoomName()).willReturn(room);
        given(h.getGuestName()).willReturn(guest);
        given(h.getStartAt()).willReturn(start);
        given(h.getEndAt()).willReturn(end);
        given(h.getAmount()).willReturn(amount);
        given(h.getStatus()).willReturn(status);
        return h;
    }

    private ReservationChargeItem item(String kind, String desc,
                                       LocalDateTime s, LocalDateTime e,
                                       int unit, int amt) {
        ReservationChargeItem i = new ReservationChargeItem();
        i.setKind(kind);
        i.setDescription(desc);
        i.setSliceStart(s);
        i.setSliceEnd(e);
        i.setUnitRatePerHour(unit);
        i.setSliceAmount(amt);
        return i;
    }

    @Nested
    class ExportListCsv {

        @Test
        @DisplayName("一覧CSV: デフォルト(onlyWithItems=true, roomId=null) 引数/ヘッダ/BOM/本文OK")
        void list_success_defaultFilters() throws Exception {
            int hostId = 12;

            var p1 = listRow(101, 1, "Aスタジオ", "山田太郎",
                    LocalDateTime.of(2025, 10, 1, 9, 0),
                    LocalDateTime.of(2025, 10, 1, 11, 0),
                    6000, "PAID");
            var p2 = listRow(102, 2, "Bスタジオ", "佐藤花子",
                    LocalDateTime.of(2025, 10, 2, 18, 0),
                    LocalDateTime.of(2025, 10, 2, 20, 0),
                    7000, "PENDING");

            given(reservationRepository.findSalesDetailsForHost(eq(hostId), eq(1), isNull(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(p1, p2)));

            var result = mvc.perform(
                    get("/host/sales_details.csv").with(user(detailsOf(hostId)))
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"host-sales.csv\""))
            .andReturn();

            String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

            // BOM
            assert csv.charAt(0) == '\uFEFF' : "CSV must start with BOM";

            // ヘッダ
            String firstLine = csv.lines().findFirst().orElse("");
            assert firstLine.equals("\uFEFF" + String.join(",",
                    "予約ID","スタジオ名","予約者","予約開始時刻","予約終了日時","総額(円)","状態"));

            // 本文（フォーマット yyyy-MM-dd HH:mm）
            assert csv.contains("\"101\",\"Aスタジオ\",\"山田太郎\",\"2025-10-01 09:00\",\"2025-10-01 11:00\",\"6000\",\"PAID\"");
            assert csv.contains("\"102\",\"Bスタジオ\",\"佐藤花子\",\"2025-10-02 18:00\",\"2025-10-02 20:00\",\"7000\",\"PENDING\"");

            then(reservationRepository).should()
                    .findSalesDetailsForHost(eq(hostId), eq(1), isNull(), any(Pageable.class));
        }

        @Test
        @DisplayName("一覧CSV: roomId指定＋onlyWithItems=false（引数の整合性）")
        void list_withFilters() throws Exception {
            int hostId = 9;
            Integer roomId = 55;

            given(reservationRepository.findSalesDetailsForHost(eq(hostId), eq(0), eq(roomId), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of()));

            mvc.perform(
                    get("/host/sales_details.csv")
                            .param("roomId", String.valueOf(roomId))
                            .param("onlyWithItems", "false")
                            .with(user(detailsOf(hostId)))
            )
            .andExpect(status().isOk());

            then(reservationRepository).should()
                    .findSalesDetailsForHost(eq(hostId), eq(0), eq(roomId), any(Pageable.class));
        }
    }

    @Nested
    class ExportItemsCsv {

        @Test
        @DisplayName("明細CSV: 予約ヘッダメタ行＋明細ヘッダ/本文 OK")
        void items_success() throws Exception {
            int hostId = 44;
            int reservationId = 9001;

            var h = head(reservationId, "Cスタジオ", "田中一郎",
                    LocalDateTime.of(2025, 9, 10, 13, 0),
                    LocalDateTime.of(2025, 9, 10, 15, 0),
                    8000, "PAID");

            given(reservationRepository.findSalesHeadOne(hostId, reservationId))
                    .willReturn(Optional.of(h));

            var i1 = item("BASE", "基本料金", LocalDateTime.of(2025, 9, 10, 13, 0),
                    LocalDateTime.of(2025, 9, 10, 14, 0), 4000, 4000);
            var i2 = item("BASE", "基本料金", LocalDateTime.of(2025, 9, 10, 14, 0),
                    LocalDateTime.of(2025, 9, 10, 15, 0), 4000, 4000);
            given(chargeItemRepository.findByReservationIdOrderBySliceStartAsc(reservationId))
                    .willReturn(List.of(i1, i2));

            var result = mvc.perform(
                    get("/host/sales_details/{id}/items.csv", reservationId).with(user(detailsOf(hostId)))
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv; charset=UTF-8"))
            .andExpect(header().string("Content-Disposition",
                    "attachment; filename=\"reservation-" + reservationId + "-items.csv\""))
            .andReturn();

            String csv = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

            // 先頭メタ行
            assert csv.contains("# 予約ID," + reservationId);
            assert csv.contains("# スタジオ名,\"Cスタジオ\"");
            assert csv.contains("# 予約者,\"田中一郎\"");
            assert csv.contains("# 期間,2025-09-10 13:00 〜 2025-09-10 15:00");
            assert csv.contains("# 総額(円),8000");

            // 明細
            assert csv.contains("区分,明細内容,開始,終了,1時間当たりの値段,金額(円)");
            assert csv.contains("\"BASE\",\"基本料金\",\"2025-09-10 13:00\",\"2025-09-10 14:00\",\"4000\",\"4000\"");
            assert csv.contains("\"BASE\",\"基本料金\",\"2025-09-10 14:00\",\"2025-09-10 15:00\",\"4000\",\"4000\"");

            then(reservationRepository).should().findSalesHeadOne(hostId, reservationId);
            then(chargeItemRepository).should().findByReservationIdOrderBySliceStartAsc(reservationId);
        }

        @Test
        @DisplayName("明細CSV: 見つからない → 現状実装は RuntimeException で ServletException が再スロー")
        void items_notFound_currentImpl_throwsServletException() throws Exception {
            int hostId = 44;
            int reservationId = 9999;

            given(reservationRepository.findSalesHeadOne(hostId, reservationId))
                    .willReturn(Optional.empty());

            assertThrows(ServletException.class, () ->
                mvc.perform(get("/host/sales_details/{id}/items.csv", reservationId)
                        .with(user(detailsOf(hostId))))
                   .andReturn()
            );

            then(reservationRepository).should().findSalesHeadOne(hostId, reservationId);
            then(chargeItemRepository).should(never()).findByReservationIdOrderBySliceStartAsc(anyInt());
        }
    }
}

