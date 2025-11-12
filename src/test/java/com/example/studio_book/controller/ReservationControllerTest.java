// src/test/java/com/example/studio_book/controller/ReservationControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.dto.ReservationConfirmDto;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.form.ReservationInputForm;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomBusinessHourRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.ReservationService;
import com.example.studio_book.service.StripeService;
import com.example.studio_book.validation.ReservationInputValidator;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionRetrieveParams;

import jakarta.servlet.http.HttpSession;

@WebMvcTest(ReservationController.class)
@TestPropertySource(properties = {
    "stripe.publishable-key=pk_test_12345"
})
class ReservationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean ReservationService reservationService;
    @MockBean StripeService stripeService;
    @MockBean RoomRepository roomRepository;
    @MockBean ReviewRepository reviewRepository;
    @MockBean RoomBusinessHourRepository roomBusinessHourRepository;
    @MockBean PriceRuleRepository priceRuleRepository;
    @MockBean ReservationInputValidator reservationInputValidator;

    // ==== ヘルパ（衝突回避のため user -> userEntity に改名） ====
    private static User userEntity(int id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setName("Taro");
        return u;
    }
    private static UserDetailsImpl principalOf(User u) {
        return new UserDetailsImpl(u, List.of(() -> "ROLE_GENERAL"));
    }
    private static Room room(int id, String name) {
        Room r = new Room();
        r.setId(id);
        r.setName(name);
        r.setPrice(5000); // ベース価格（int）
        return r;
    }
    

    // ----------------------------
    // GET /reservations
    // ----------------------------
    @Nested
    @DisplayName("GET /reservations")
    class Index {

        @Test
        @DisplayName("一覧が表示できる（reservationPageあり、reserved/processingなし）")
        void index_basic() throws Exception {
            User me = userEntity(10, "me@example.com");
            Page<Reservation> page = new PageImpl<>(List.of(), PageRequest.of(0,10), 0);

            given(reservationService.findReservationsByUserOrderByCreatedAtDesc(eq(me), any()))
                .willReturn(page);

            mvc.perform(get("/reservations")
                    .with(user(principalOf(me))).with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("reservations/index"))
               .andExpect(model().attributeExists("reservationPage"))
               .andExpect(model().attributeDoesNotExist("reserved","processing"));
        }

        @Test
        @DisplayName("reserved=1 だが session_id なし → processing=true")
        void index_reserved_processing_when_no_session_id() throws Exception {
            User me = userEntity(11, "me@example.com");
            given(reservationService.findReservationsByUserOrderByCreatedAtDesc(eq(me), any()))
                .willReturn(new PageImpl<>(List.of()));

            mvc.perform(get("/reservations")
                    .param("reserved","1")
                    .with(user(principalOf(me))).with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("reservations/index"))
               .andExpect(model().attributeExists("reservationPage","processing"))
               .andExpect(model().attributeDoesNotExist("reserved"));
        }

        @Test
        @DisplayName("reserved & session_id あり → 既にPIが保存済みなら reserved=true")
        void index_reserved_true_when_pi_exists() throws Exception {
            User me = userEntity(12, "me@example.com");
            given(reservationService.findReservationsByUserOrderByCreatedAtDesc(eq(me), any()))
                .willReturn(new PageImpl<>(List.of()));

            try (MockedStatic<Session> mocked = Mockito.mockStatic(Session.class)) {
                Session fake = new Session();
                fake.setPaymentIntent("pi_123");

                mocked.when(() -> Session.retrieve(eq("cs_test_abc"), any(SessionRetrieveParams.class), isNull()))
                      .thenReturn(fake);

                given(reservationService.existsByPaymentIntentId("pi_123")).willReturn(true);

                mvc.perform(get("/reservations")
                        .param("reserved","1")
                        .param("session_id","cs_test_abc")
                        .with(user(principalOf(me))).with(csrf()))
                   .andExpect(status().isOk())
                   .andExpect(view().name("reservations/index"))
                   .andExpect(model().attributeExists("reservationPage","reserved"))
                   .andExpect(model().attributeDoesNotExist("processing"));
            }
        }
    }

    // ----------------------------
    // GET /reservations/confirm
    // ----------------------------
    @Nested
    @DisplayName("GET /reservations/confirm")
    class Confirm {

        @Test
        @DisplayName("セッションDTOなし → /rooms へリダイレクト＆フラッシュメッセージ")
        void confirm_no_session_dto_redirects() throws Exception {
            User me = userEntity(20, "me@example.com");

            mvc.perform(get("/reservations/confirm")
                    .with(user(principalOf(me))).with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/rooms"))
               .andExpect(flash().attributeExists("errorMessage"));
        }

        @Test
        @DisplayName("DTOあり & StripeセッションOK → confirm画面")
        void confirm_ok() throws Exception {
            User me = userEntity(21, "me@example.com");
            MockHttpSession session = new MockHttpSession();

            ReservationConfirmDto dto = ReservationConfirmDto.builder()
                    .roomId(101)
                    .roomName("Room A")
                    .startAt(LocalDateTime.now().plusDays(1))
                    .endAt(LocalDateTime.now().plusDays(1).plusHours(2))
                    .hourlyPrice(4000)   // Integer
                    .hours(2L)           // Long
                    .amount(8000L)       // Long
                    .build();

            session.setAttribute("reservationDTO", dto);

            given(stripeService.createStripeSession(eq(dto), eq(me))).willReturn("cs_test_987");

            mvc.perform(get("/reservations/confirm")
                    .session(session)
                    .with(user(principalOf(me))).with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("reservations/confirm"))
               .andExpect(model().attribute("confirm", dto))
               .andExpect(model().attribute("sessionId", "cs_test_987"))
               .andExpect(model().attributeExists("stripePublishableKey"));
        }

        @Test
        @DisplayName("Stripeセッションが空 → /rooms/{roomId} リダイレクト＆フラッシュ")
        void confirm_stripe_session_blank_redirects_back() throws Exception {
            User me = userEntity(22, "me@example.com");
            MockHttpSession session = new MockHttpSession();

            ReservationConfirmDto dto = ReservationConfirmDto.builder()
                    .roomId(201)
                    .roomName("Room B")
                    .startAt(LocalDateTime.now().plusDays(1))
                    .endAt(LocalDateTime.now().plusDays(1).plusHours(1))
                    .hourlyPrice(4000)
                    .hours(1L)
                    .amount(4000L)
                    .build();

            session.setAttribute("reservationDTO", dto);
            given(stripeService.createStripeSession(eq(dto), eq(me))).willReturn("");

            mvc.perform(get("/reservations/confirm")
                    .session(session)
                    .with(user(principalOf(me))).with(csrf()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/rooms/201"))
               .andExpect(flash().attributeExists("errorMessage"));
        }
    }

    // ----------------------------
    // POST /rooms/{roomId}/reservations/input
    // ----------------------------
    @Nested
    @DisplayName("POST /rooms/{roomId}/reservations/input")
    class Input {

        @Test
        @DisplayName("BeanValidationエラー → rooms/show 再描画（モデル充足）")
        void input_binding_errors_rerender_room_show() throws Exception {
            int roomId = 300;
            User me = userEntity(30, "me@example.com");

            given(roomRepository.findById(roomId)).willReturn(Optional.of(room(roomId,"Room C")));
            given(roomBusinessHourRepository.findByRoomIdOrderByDayIndexAsc(roomId)).willReturn(List.of());
            given(reviewRepository.findByRoomIdAndPublicVisibleTrueOrderByCreatedAtDesc(eq(roomId), any()))
                .willReturn(new PageImpl<>(List.of()));
            given(reviewRepository.findByRoomIdAndPublicVisibleFalseAndHostReplyIsNotNullOrderByHostReplyAtDesc(roomId))
                .willReturn(List.of());
            given(reviewRepository.findAveragePublicScoreByRoomId(roomId)).willReturn(4.2);
            given(reviewRepository.countByRoomIdAndPublicVisibleTrue(roomId)).willReturn(3L);
            given(priceRuleRepository.findByRoomId(roomId)).willReturn(List.of());

            mvc.perform(post("/rooms/{roomId}/reservations/input", roomId)
                    .with(user(principalOf(me))).with(csrf())
                    .param("dummy","x")) // 必須未入力で BeanValidation エラーを誘発
               .andExpect(status().isOk())
               .andExpect(view().name("rooms/show"))
               .andExpect(model().attributeExists("room","businessHours","reviewsPage",
                                                 "hiddenWithReply","avgScore","reviewCount",
                                                 "flatFeeRules","multiplierRules"));

            then(reservationInputValidator)
                .should(never())
                .validateWithRoomId(any(ReservationInputForm.class), any(), eq(roomId));
        }

        @Test
        @DisplayName("バリデOK & buildConfirmDto成功 → セッション保存して /reservations/confirm")
        void input_ok_redirects_to_confirm() throws Exception {
            int roomId = 301;
            User me = userEntity(31, "me@example.com");

            var start = LocalDateTime.parse("2030-01-01T10:00");
            var end   = LocalDateTime.parse("2030-01-01T12:00");

            ReservationConfirmDto dto = ReservationConfirmDto.builder()
                    .roomId(roomId)
                    .roomName("Room D")
                    .startAt(start)
                    .endAt(end)
                    .hourlyPrice(5000)
                    .hours(2L)
                    .amount(10000L)
                    .build();

            given(reservationService.buildConfirmDto(eq(roomId), eq(me), any(), any()))
                    .willReturn(dto);

            var result = mvc.perform(post("/rooms/{roomId}/reservations/input", roomId)
                    .with(user(principalOf(me))).with(csrf())
                    .param("startDate", "2030-01-01")
                    .param("startTime", "10:00")
                    .param("endDate",   "2030-01-01")
                    .param("endTime",   "12:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/reservations/confirm"))
                .andReturn();

            HttpSession httpSession = result.getRequest().getSession(false);
            assertThat(httpSession).isNotNull();
            assertThat(httpSession.getAttribute("reservationDTO")).isEqualTo(dto);
        }


        @Test
        @DisplayName("buildConfirmDto が例外 → /rooms/{roomId} リダイレクト＆フラッシュ")
        void input_buildConfirmDto_throws_redirects_back() throws Exception {
            int roomId = 302;
            User me = userEntity(32, "me@example.com");

            given(reservationService.buildConfirmDto(eq(roomId), eq(me), any(), any()))
                    .willThrow(new RuntimeException("calc error"));

            mvc.perform(post("/rooms/{roomId}/reservations/input", roomId)
                    .with(user(principalOf(me))).with(csrf())
                    .param("startDate", "2030-01-01")
                    .param("startTime", "10:00")
                    .param("endDate",   "2030-01-01")
                    .param("endTime",   "11:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rooms/302"))
                .andExpect(flash().attributeExists("errorMessage"));
        }

    }
}

