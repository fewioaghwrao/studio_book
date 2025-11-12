// src/test/java/com/example/studio_book/controller/HostReservationControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.example.studio_book.dto.HostReservationRow;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.security.UserDetailsImpl;

public class HostReservationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ReservationRepository reservationRepository;

    private HostReservationController target;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        target = new HostReservationController(reservationRepository);

        // ダミービュー解決（Thymeleafに依存しない）
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders
                .standaloneSetup(target)
                // @AuthenticationPrincipal を解決
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setViewResolvers(viewResolver)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        if (mocks != null) mocks.close();
    }

    // ---- Helpers ---------------------------------------------------------

    // 認証主体（ROLE_HOST）を作る
    private UserDetailsImpl hostPrincipal(int hostId) {
        User u = new User();
        u.setId(hostId);
        u.setEmail("host@example.com");
        u.setPassword("{noop}pw");
        return new UserDetailsImpl(
                u,
                List.of(new SimpleGrantedAuthority("ROLE_HOST"))
        );
    }

    // SecurityContextHolder に直接 Authentication を積む（standaloneSetup 用）
    private RequestPostProcessor auth(UserDetailsImpl principal) {
        return request -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, principal.getPassword(), principal.getAuthorities());
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    // 一覧DTOを作る（あなたの HostReservationRow に準拠）
    private HostReservationRow row(
            int id,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int amount
    ) {
        return new HostReservationRow(
                id,
                "Room A",     // roomName
                "Taro",       // guestName
                startAt,
                endAt,
                amount,
                status
        );
    }

    // 承認/取消で使うエンティティ
    private Reservation entity(int id, int hostId, String status) {
        User host = new User();
        host.setId(hostId);
        Room room = new Room();
        room.setId(10);
        room.setUser(host);

        Reservation r = new Reservation();
        r.setId(id);
        r.setRoom(room);
        r.setStatus(status);
        r.setStartAt(LocalDateTime.of(2025, 11, 1, 9, 0));
        return r;
    }

    // ---- Tests -----------------------------------------------------------

    @Nested
    @DisplayName("GET /host/reservations")
    class Index {

        @Test
        @DisplayName("検索パラメータの正規化・ページング・モデル属性・ビュー名")
        void index_success() throws Exception {
            int hostId = 777;
            var principal = hostPrincipal(hostId);

            Page<HostReservationRow> page = new PageImpl<>(
                    List.of(
                            row(
                                    1,
                                    "booked",
                                    LocalDateTime.of(2025, 11, 20, 10, 0),
                                    LocalDateTime.of(2025, 11, 20, 12, 0),
                                    5000
                            )
                    )
            );

            given(reservationRepository.findPageForHostFiltered(
                    eq(hostId),
                    eq("foo"), // kw 正規化後
                    eq("booked"),
                    eq(LocalDate.of(2025, 11, 1).atStartOfDay()),
                    eq(LocalDate.of(2025, 11, 30).atTime(23, 59, 59, 999_000_000)),
                    eq(123),
                    eq(10),
                    any() // Pageable
            )).willReturn(page);

            given(reservationRepository.findRoomOptionsForHost(hostId))
                    .willReturn(List.of());

            mockMvc.perform(get("/host/reservations")
                            .param("page", "0")
                            .param("kw", "  foo  ")
                            .param("status", "booked")
                            .param("reservationId", "123")
                            .param("roomId", "10")
                            .param("startFrom", "2025-11-01")
                            .param("startTo", "2025-11-30")
                            .with(auth(principal)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("host/reservations/index"))
                    .andExpect(model().attributeExists(
                            "page", "rows", "kw", "status", "reservationId",
                            "roomId", "startFrom", "startTo", "roomOptions"))
                    .andExpect(model().attribute("kw", "foo"))
                    .andExpect(model().attribute("status", "booked"))
                    .andExpect(model().attribute("reservationId", 123))
                    .andExpect(model().attribute("roomId", 10))
                    .andExpect(model().attribute("startFrom", "2025-11-01"))
                    .andExpect(model().attribute("startTo", "2025-11-30"));

            then(reservationRepository).should(times(1))
                    .findPageForHostFiltered(anyInt(), any(), any(), any(), any(), any(), any(), any());
            then(reservationRepository).should(times(1))
                    .findRoomOptionsForHost(hostId);
        }
    }

    @Nested
    @DisplayName("POST /host/reservations/{id}/approve")
    class Approve {

        @Test
        @DisplayName("booked → paid に遷移し、保存され、リダイレクト")
        void approve_transits_when_booked() throws Exception {
            int hostId = 777;
            int rid = 55;
            var principal = hostPrincipal(hostId);

            Reservation found = entity(rid, hostId, "booked");
            given(reservationRepository.findByIdAndRoom_User_Id(rid, hostId))
                    .willReturn(Optional.of(found));

            mockMvc.perform(post("/host/reservations/{id}/approve", rid)
                            .with(auth(principal)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/host/reservations?approved=1"));

            ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
            then(reservationRepository).should().save(captor.capture());
            Reservation saved = captor.getValue();
            org.assertj.core.api.Assertions.assertThat(saved.getStatus())
                    .isEqualToIgnoringCase("paid");
        }

        @Test
        @DisplayName("booked 以外（paid等）は変更なし（save未呼び出し）")
        void approve_noop_when_not_booked() throws Exception {
            int hostId = 777;
            int rid = 56;
            var principal = hostPrincipal(hostId);

            Reservation found = entity(rid, hostId, "paid");
            given(reservationRepository.findByIdAndRoom_User_Id(rid, hostId))
                    .willReturn(Optional.of(found));

            mockMvc.perform(post("/host/reservations/{id}/approve", rid)
                            .with(auth(principal)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/host/reservations?approved=1"));

            then(reservationRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("他人/未存在 → 404")
        void approve_404_when_not_found() throws Exception {
            int hostId = 777;
            int rid = 57;
            var principal = hostPrincipal(hostId);

            given(reservationRepository.findByIdAndRoom_User_Id(rid, hostId))
                    .willReturn(Optional.empty());

            mockMvc.perform(post("/host/reservations/{id}/approve", rid)
                            .with(auth(principal)))
                    .andExpect(result -> {
                        if (!(result.getResolvedException() instanceof ResponseStatusException ex) ||
                                ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                            throw new AssertionError("Expected 404 NOT_FOUND ResponseStatusException");
                        }
                    });
        }
    }

    @Nested
    @DisplayName("POST /host/reservations/{id}/cancel")
    class Cancel {

        @Test
        @DisplayName("booked → canceled に遷移し、保存され、リダイレクト")
        void cancel_transits_when_booked() throws Exception {
            int hostId = 777;
            int rid = 66;
            var principal = hostPrincipal(hostId);

            Reservation found = entity(rid, hostId, "booked");
            given(reservationRepository.findByIdAndRoom_User_Id(rid, hostId))
                    .willReturn(Optional.of(found));

            mockMvc.perform(post("/host/reservations/{id}/cancel", rid)
                            .with(auth(principal)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/host/reservations?canceled=1"));

            ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
            then(reservationRepository).should().save(captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().getStatus())
                    .isEqualToIgnoringCase("canceled");
        }

        @Test
        @DisplayName("booked 以外は保存されない")
        void cancel_noop_when_not_booked() throws Exception {
            int hostId = 777;
            int rid = 67;
            var principal = hostPrincipal(hostId);

            Reservation found = entity(rid, hostId, "paid");
            given(reservationRepository.findByIdAndRoom_User_Id(rid, hostId))
                    .willReturn(Optional.of(found));

            mockMvc.perform(post("/host/reservations/{id}/cancel", rid)
                            .with(auth(principal)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/host/reservations?canceled=1"));

            then(reservationRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("未存在/他人 → 404")
        void cancel_404_when_not_found() throws Exception {
            int hostId = 777;
            int rid = 68;
            var principal = hostPrincipal(hostId);

            given(reservationRepository.findByIdAndRoom_User_Id(rid, hostId))
                    .willReturn(Optional.empty());

            mockMvc.perform(post("/host/reservations/{id}/cancel", rid)
                            .with(auth(principal)))
                    .andExpect(result -> {
                        if (!(result.getResolvedException() instanceof ResponseStatusException ex) ||
                                ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                            throw new AssertionError("Expected 404 NOT_FOUND ResponseStatusException");
                        }
                    });
        }
    }
}

