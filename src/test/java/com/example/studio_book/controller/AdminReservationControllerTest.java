// src/test/java/com/example/studio_book/controller/AdminReservationControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.dto.AdminReservationRow;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.repository.ReservationRepository;

@WebMvcTest(controllers = AdminReservationController.class)
@AutoConfigureMockMvc(addFilters = false) // Security無効化（CSRFはwith(csrf())で供給）
class AdminReservationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ReservationRepository reservationRepository;

    // ---------------------------------------------------------------------
    // index() : Repositoryが Page<AdminReservationRow> を返す仕様に合わせる
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("index: フィルタなし → Page<AdminReservationRow> がModelに載る")
    void index_noFilters_ok() throws Exception {
        var start = LocalDateTime.of(2025, 1, 10, 9, 0);
        var end   = LocalDateTime.of(2025, 1, 10, 12, 0);

        var row = new AdminReservationRow(
            1, "RoomA", "GuestTaro", "HostHanako", start, end, 5000, "booked"
        );
        Page<AdminReservationRow> page = new PageImpl<>(List.of(row), Pageable.ofSize(5), 1);

        when(reservationRepository.findAdminReservationPageFiltered(
            isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(page);

        mockMvc.perform(get("/admin/reservations").with(csrf()))
        .andExpect(status().isOk())
        .andExpect(view().name("admin/reservations/index"))
        .andExpect(model().attributeExists("page", "rows"));
        // ページャ確認
        ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationRepository).findAdminReservationPageFiltered(
            isNull(), isNull(), isNull(), isNull(), isNull(), pageableCap.capture()
        );
        assertThat(pageableCap.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("index: フィルタの正規化（trim/日付変換/負のpage矯正/ID）")
    void index_filters_normalized() throws Exception {
        when(reservationRepository.findAdminReservationPageFiltered(any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        LocalDate from = LocalDate.of(2025, 1, 2);
        LocalDate to   = LocalDate.of(2025, 1, 30);

        mockMvc.perform(get("/admin/reservations").with(csrf())
                .param("kw", "  abc  ")
                .param("status", "booked")
                .param("startFrom", from.toString())
                .param("startTo", to.toString())
                .param("reservationId", "123")
                .param("page", "-1") // → 0 に矯正される想定
        ).andExpect(status().isOk());

        ArgumentCaptor<String> kwCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> stCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> fromCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCap   = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Integer> idCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);

        verify(reservationRepository).findAdminReservationPageFiltered(
            kwCap.capture(), stCap.capture(), fromCap.capture(), toCap.capture(), idCap.capture(), pageableCap.capture()
        );

        assertThat(kwCap.getValue()).isEqualTo("abc");                       // trim済み
        assertThat(stCap.getValue()).isEqualTo("booked");
        assertThat(idCap.getValue()).isEqualTo(123);
        assertThat(fromCap.getValue()).isEqualTo(from.atStartOfDay());       // 00:00
        assertThat(toCap.getValue()).isEqualTo(to.atTime(23,59,59,999_000_000)); // 23:59:59.999
        assertThat(pageableCap.getValue().getPageNumber()).isEqualTo(0);     // -1 → 0
        assertThat(pageableCap.getValue().getPageSize()).isEqualTo(5);
    }

    // ---------------------------------------------------------------------
    // approve/cancel/clear : Reservation を直接操作
    // ---------------------------------------------------------------------
    @Test
    @DisplayName("approve: status→paid & save & redirect")
    void approve_updatesToPaid() throws Exception {
        var r = new Reservation();
        r.setId(10);
        r.setStatus("booked");

        when(reservationRepository.findById(10)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/admin/reservations/{id}/approve", 10).with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/reservations?approved=1"));

        assertThat(r.getStatus()).isEqualTo("paid");
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("approve: 存在しない → 404")
    void approve_notFound() throws Exception {
        when(reservationRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(post("/admin/reservations/{id}/approve", 999).with(csrf()))
               .andExpect(status().isNotFound());

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancel: status→canceled & save & redirect")
    void cancel_updatesToCanceled() throws Exception {
        var r = new Reservation();
        r.setId(20);
        r.setStatus("booked");

        when(reservationRepository.findById(20)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/admin/reservations/{id}/cancel", 20).with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/reservations?canceled=1"));

        assertThat(r.getStatus()).isEqualTo("canceled");
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("clear: status→booked & save & redirect")
    void clear_updatesToBooked() throws Exception {
        var r = new Reservation();
        r.setId(30);
        r.setStatus("paid");

        when(reservationRepository.findById(30)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/admin/reservations/{id}/clear", 30).with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/admin/reservations?cleared=1"));

        assertThat(r.getStatus()).isEqualTo("booked");
        verify(reservationRepository).save(r);
    }
}


