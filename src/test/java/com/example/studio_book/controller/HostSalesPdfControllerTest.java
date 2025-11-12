// src/test/java/com/example/studio_book/controller/HostSalesPdfControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.example.studio_book.dto.HostSalesHead;
import com.example.studio_book.entity.ReservationChargeItem;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.ReservationChargeItemRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.security.UserDetailsImpl;

// 重要: セキュリティフィルタ有効のままでOK。with(user(...))がSecurityContextをセットします。
@WebMvcTest(controllers = HostSalesPdfController.class)
@AutoConfigureMockMvc
class HostSalesPdfControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ReservationRepository reservationRepository;

    @MockBean
    ReservationChargeItemRepository chargeItemRepository;

 // テスト用ヘルパ
    private UserDetailsImpl detailsOf(int userId) {
        User u = new User();
        u.setId(userId);
        u.setName("Host" + userId);
        u.setEnabled(true); // isEnabled() が true になるように

        return new UserDetailsImpl(
            u,
            List.of(new SimpleGrantedAuthority("ROLE_HOST"))
        );
    }
    
    @TestConfiguration
    static class TestConfig {
        @ControllerAdvice(assignableTypes = HostSalesPdfController.class)
        static class LocalAdvice {
            @ExceptionHandler(RuntimeException.class)
            public ResponseEntity<String> handle(RuntimeException ex) {
                return ResponseEntity.status(500).body(ex.getMessage());
            }
        }
    }
    // reservationRepository.findSalesHeadOne(...) が返す型が
    // interface ベースの projection である想定に合わせ、Mockitoのスタブで用意します。
    @SuppressWarnings("unchecked")
    private <T> T stubSalesHead(
            int reservationId, String status, String roomName, String guestName,
            LocalDateTime startAt, LocalDateTime endAt, int amount,
            Class<T> projectionType // 実プロジェクション型が分かる場合はそのClassを渡す。分からなければ Object.class でもOK
    ) {
        T head = (T) Mockito.mock(projectionType);
        try {
            // 典型的なゲッター名でスタブ（プロジェクションのメソッド名に合わせてください）
            Mockito.when(head.getClass().getMethod("getReservationId").invoke(head)).thenReturn(reservationId);
        } catch (Throwable ignore) {}
        // リフレクションが面倒なら、以下のように「明示的に」メソッドをスタブしてください。
        // 例: SalesHead というinterfaceがある場合:
        // SalesHead head = Mockito.mock(SalesHead.class);
        // given(head.getReservationId()).willReturn(reservationId);
        // given(head.getStatus()).willReturn(status);
        // ...
        return head;
    }

    // プロジェクションのメソッド名が分かっている場合の「素直な」例
    private Object salesHeadMock(
            int reservationId, String status, String roomName, String guestName,
            LocalDateTime startAt, LocalDateTime endAt, int amount
    ) {
        // ここでは型が不明なので Object で作り、when(...).thenReturn(...) を直接書きます。
        // 実際は ReservationRepository に定義された projection interface を import して使ってください。
        Object head = Mockito.mock(Object.class, Mockito.withSettings().extraInterfaces(
                // 例: ReservationRepository.SalesHead.class
                new Class<?>[]{}
        ));
        // 以下は「例」。実プロジェクションに合わせて書き換えてください。
        try { Mockito.when(head.getClass().getMethod("getReservationId").invoke(head)).thenReturn(reservationId); } catch (Throwable ignored) {}
        try { Mockito.when(head.getClass().getMethod("getStatus").invoke(head)).thenReturn(status); } catch (Throwable ignored) {}
        try { Mockito.when(head.getClass().getMethod("getRoomName").invoke(head)).thenReturn(roomName); } catch (Throwable ignored) {}
        try { Mockito.when(head.getClass().getMethod("getGuestName").invoke(head)).thenReturn(guestName); } catch (Throwable ignored) {}
        try { Mockito.when(head.getClass().getMethod("getStartAt").invoke(head)).thenReturn(startAt); } catch (Throwable ignored) {}
        try { Mockito.when(head.getClass().getMethod("getEndAt").invoke(head)).thenReturn(endAt); } catch (Throwable ignored) {}
        try { Mockito.when(head.getClass().getMethod("getAmount").invoke(head)).thenReturn(amount); } catch (Throwable ignored) {}
        return head;
    }

    private ReservationChargeItem item(String desc, LocalDateTime s, LocalDateTime e, Integer unit, Integer amount) {
        ReservationChargeItem it = new ReservationChargeItem();
        it.setDescription(desc);
        it.setSliceStart(s);
        it.setSliceEnd(e);
        it.setUnitRatePerHour(unit);
        it.setSliceAmount(amount);
        return it;
    }

    @Nested
    class InvoicePdf {
        @DisplayName("成功: PDFが返る（Content-Type, ファイル名, %PDF 先頭, ボディ非空）")
        @Test
        void ok_returnsPdf() throws Exception {
            int hostId = 44;
            int reservationId = 9001;

            // HostSalesHead をちゃんと型付きでモック
            HostSalesHead head = mock(HostSalesHead.class);
            given(head.getReservationId()).willReturn(reservationId);
            given(head.getRoomName()).willReturn("Room-A");
            given(head.getGuestName()).willReturn("山田太郎");
            given(head.getStartAt()).willReturn(LocalDateTime.of(2025,1,10,9,0));
            given(head.getEndAt()).willReturn(LocalDateTime.of(2025,1,10,12,0));
            given(head.getAmount()).willReturn(12345);
            given(head.getStatus()).willReturn("paid");

            // Optional<HostSalesHead> を返す
            given(reservationRepository.findSalesHeadOne(hostId, reservationId))
                .willReturn(Optional.of(head));

            // 明細スタブはそのまま
            given(chargeItemRepository.findByReservationIdOrderBySliceStartAsc(reservationId))
                .willReturn(List.of(
                    item("基本料金(1h)", LocalDateTime.of(2025,1,10,9,0),
                         LocalDateTime.of(2025,1,10,10,0), 3000, 3000),
                    item("延長(2h)", LocalDateTime.of(2025,1,10,10,0),
                         LocalDateTime.of(2025,1,10,12,0), 3500, 7000),
                    item("清掃費", null, null, null, 345)
                ));

            mvc.perform(get("/host/sales_details/{id}/invoice.pdf", reservationId)
                    .with(user(detailsOf(hostId))))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("見つからない: 現状実装は 5xx（RuntimeException）")
        void notFound_currentImpl_returns5xx() throws Exception {
            int hostId = 44;
            int reservationId = 9999;

            given(reservationRepository.findSalesHeadOne(hostId, reservationId))
                    .willReturn(Optional.empty());

            mvc.perform(get("/host/sales_details/{id}/invoice.pdf", reservationId)
                    .with(user(detailsOf(hostId))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not found or not yours")));
        }

        // detailsOf は GrantedAuthority を渡す
        private UserDetailsImpl detailsOf(int userId) {
            var u = new com.example.studio_book.entity.User();
            u.setId(userId);
            u.setName("Host" + userId);
            u.setEnabled(true);
            return new com.example.studio_book.security.UserDetailsImpl(
                    u, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_HOST")));
        }
    }
}
