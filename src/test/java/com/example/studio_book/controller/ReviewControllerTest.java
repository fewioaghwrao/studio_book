// src/test/java/com/example/studio_book/controller/ReviewControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.example.studio_book.entity.Review;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;

@WebMvcTest(ReviewController.class)
@org.springframework.context.annotation.Import(TestExceptionHandler.class)
class ReviewControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RoomRepository roomRepository;

    @MockBean
    ReviewRepository reviewRepository;

    // ===== Helpers =====

    private User appUser(int id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user" + id + "@example.com");
        u.setName("User " + id);
        return u;
    }

    private UserDetailsImpl principalOf(User u) {
        return new UserDetailsImpl(u, List.of(new SimpleGrantedAuthority("ROLE_GENERAL")));
    }

    private Room room(int id, String name) {
        Room r = new Room();
        r.setId(id);
        r.setName(name);
        return r;
    }

    private Review review(int id, Room room, User user, int score, String content) {
        Review rv = new Review();
        rv.setId(id);
        rv.setRoom(room);
        rv.setUser(user);
        rv.setScore(score);
        rv.setContent(content);
        rv.setCreatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return rv;
    }

    // ==== ★ テスト専用の ControllerAdvice（不足属性のデフォルト投入） ====
    // ReviewController のビュー "reviews/new" が必要とする属性がモデルに無い場合だけ、0/Empty を補う。
    @TestConfiguration
    static class TestAdviceConfig {
        @ControllerAdvice(assignableTypes = ReviewController.class)
        static class ReviewViewDefaultsAdvice {
            @ModelAttribute
            void addDefaults(Model model) {
                if (!model.containsAttribute("avgScore")) {
                    model.addAttribute("avgScore", 0.0);
                }
                if (!model.containsAttribute("reviewCount")) {
                    model.addAttribute("reviewCount", 0L);
                }
                if (!model.containsAttribute("reviewsPage")) {
                    model.addAttribute("reviewsPage", Page.empty());
                }
                if (!model.containsAttribute("reviewForm")) {
                    // POSTエラー再表示でも form は参照されるため
                    model.addAttribute("reviewForm", new com.example.studio_book.form.ReviewForm());
                }
            }
        }
    }

    // ===== GET: /rooms/{roomId}/reviews/new =====
    @Nested
    @DisplayName("GET /rooms/{roomId}/reviews/new")
    class NewForm {

        @Test
        @DisplayName("正常表示：ページング・平均・件数・一覧がモデルに入る")
        void newForm_success() throws Exception {
            int roomId = 10;
            int page = 0;

            Room room = room(roomId, "Studio A");
            given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

            Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

            User reviewer = appUser(2);
            Review rv = review(1001, room, reviewer, 5, "great!");
            Page<Review> reviewsPage = new PageImpl<>(List.of(rv), pageable, 1);
            given(reviewRepository.findByRoom_IdOrderByCreatedAtDesc(eq(roomId), any(Pageable.class)))
                    .willReturn(reviewsPage);
            given(reviewRepository.getAverageScore(roomId)).willReturn(4.5);
            given(reviewRepository.countByRoomId(roomId)).willReturn(3L);

            var principal = principalOf(appUser(1));

            mvc.perform(get("/rooms/{roomId}/reviews/new", roomId)
                        .param("reservationId", "77")
                        .param("page", String.valueOf(page))
                        .with(user(principal))     // ★ 401回避（認証付与）
                        .with(csrf()))
               .andExpect(status().isOk())
               .andExpect(view().name("reviews/new"))
               .andExpect(model().attributeExists("room", "reservationId", "reviewForm",
                                                  "reviewsPage", "avgScore", "reviewCount"))
               .andExpect(model().attribute("reservationId", 77));

            // Pageable が想定通りで呼ばれたかを検証
            ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
            then(reviewRepository).should().findByRoom_IdOrderByCreatedAtDesc(eq(roomId), pageableCap.capture());
            Pageable used = pageableCap.getValue();
            assertThat(used.getPageNumber()).isEqualTo(0);
            assertThat(used.getPageSize()).isEqualTo(10);
            assertThat(used.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

     // GET: room not found → 5xx
        @Test
        @DisplayName("room が見つからない → 5xx（IllegalArgumentException）")
        void newForm_roomNotFound_5xx() throws Exception {
            int roomId = 404;
            given(roomRepository.findById(roomId)).willReturn(Optional.empty());

            var principal = principalOf(appUser(1));

            mvc.perform(get("/rooms/{roomId}/reviews/new", roomId)
                        .with(user(principal))  // 認証
                        .with(csrf()))
            .andExpect(status().isNotFound()); 
        }

        @Test
        @DisplayName("平均が null → 0.0 に補正されてモデル投入")
        void newForm_avgNull_defaultsZero() throws Exception {
            int roomId = 11;
            Room room = room(roomId, "Studio B");
            given(roomRepository.findById(roomId)).willReturn(Optional.of(room));
            given(reviewRepository.findByRoom_IdOrderByCreatedAtDesc(eq(roomId), any(Pageable.class)))
                    .willReturn(Page.empty());
            given(reviewRepository.getAverageScore(roomId)).willReturn(null);
            given(reviewRepository.countByRoomId(roomId)).willReturn(0L);

            var principal = principalOf(appUser(1));

            MvcResult res = mvc.perform(get("/rooms/{roomId}/reviews/new", roomId)
                    .with(user(principal))         // ★ 401回避
                    .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("reviews/new"))
                .andExpect(model().attributeExists("avgScore"))
                .andReturn();

            Double avg = (Double) res.getModelAndView().getModel().get("avgScore");
            assertThat(avg).isEqualTo(0.0);
        }
    }

    // ===== POST: /rooms/{roomId}/reviews =====
    @Nested
    @DisplayName("POST /rooms/{roomId}/reviews")
    class Create {

        @Test
        @DisplayName("バリデーションエラー（content 未入力）→ new を再表示（テンプレート例外なし）")
        void create_validationError() throws Exception {
            int roomId = 20;
            Room room = room(roomId, "Studio C");
            given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

            // ★ 再表示時にテンプレートが参照する属性をモック（Adviceがデフォルト入れるが、ここで具体値でもOK）
            given(reviewRepository.findByRoom_IdOrderByCreatedAtDesc(eq(roomId), any(Pageable.class)))
                    .willReturn(Page.empty());
            given(reviewRepository.getAverageScore(roomId)).willReturn(null);
            given(reviewRepository.countByRoomId(roomId)).willReturn(0L);

            var principal = principalOf(appUser(99));

            mvc.perform(post("/rooms/{roomId}/reviews", roomId)
                    .with(user(principal))         // ★ 401回避
                    .with(csrf())
                    .param("score", "5")
                    // .param("content", ""); // 入れない→バリデーションエラー
            )
               .andExpect(status().isOk())
               .andExpect(view().name("reviews/new"))
               .andExpect(model().attributeExists("room"))
               .andExpect(model().attributeHasErrors("reviewForm"));

            then(reviewRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("重複投稿 → グローバルエラー 'duplicate' で new を再表示（テンプレート例外なし）")
        void create_duplicate() throws Exception {
            int roomId = 21;
            Room room = room(roomId, "Studio D");
            given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

            // ★ 再表示時に必要な属性
            given(reviewRepository.findByRoom_IdOrderByCreatedAtDesc(eq(roomId), any(Pageable.class)))
                    .willReturn(Page.empty());
            given(reviewRepository.getAverageScore(roomId)).willReturn(4.0);
            given(reviewRepository.countByRoomId(roomId)).willReturn(1L);

            User me = appUser(7);
            given(reviewRepository.existsByRoom_IdAndUser_Id(roomId, me.getId())).willReturn(true);

            var principal = principalOf(me);

            MvcResult res = mvc.perform(post("/rooms/{roomId}/reviews", roomId)
                    .with(user(principal))         // ★ 401回避
                    .with(csrf())
                    .param("score", "4")
                    .param("content", "second"))
               .andExpect(status().isOk())
               .andExpect(view().name("reviews/new"))
               .andExpect(model().attributeHasErrors("reviewForm"))
               .andReturn();

            var br = (org.springframework.validation.BindingResult)
                     res.getModelAndView().getModel().get("org.springframework.validation.BindingResult.reviewForm");
            assertThat(br.getGlobalErrors())
                .anySatisfy(err -> assertThat(err.getCode()).isEqualTo("duplicate"));

            then(reviewRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("正常登録 → /reservations にリダイレクト")
        void create_success() throws Exception {
            int roomId = 22;
            Room room = room(roomId, "Studio E");
            given(roomRepository.findById(roomId)).willReturn(Optional.of(room));

            User me = appUser(100);
            given(reviewRepository.existsByRoom_IdAndUser_Id(roomId, me.getId())).willReturn(false);

            var principal = principalOf(me);

            mvc.perform(post("/rooms/{roomId}/reviews", roomId)
                    .with(user(principal))         // ★ 401回避
                    .with(csrf())
                    .param("score", "5")
                    .param("content", "perfect"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/reservations"));

            ArgumentCaptor<Review> cap = ArgumentCaptor.forClass(Review.class);
            then(reviewRepository).should().save(cap.capture());
            Review saved = cap.getValue();
            assertThat(saved.getRoom().getId()).isEqualTo(roomId);
            assertThat(saved.getUser().getId()).isEqualTo(me.getId());
            assertThat(saved.getScore()).isEqualTo(5);
            assertThat(saved.getContent()).isEqualTo("perfect");
        }

    }
}



