// src/test/java/com/example/studio_book/controller/HostReviewManageControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.example.studio_book.entity.Review;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.AuditLogRepository;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;

@WebMvcTest(controllers = HostReviewManageController.class)
@AutoConfigureMockMvc(addFilters = false) // フィルタは無効。認証はテスト側で直接セット
class HostReviewManageControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean ReviewRepository reviewRepository;
    @MockBean AuditLogRepository auditLogRepository;
    @MockBean RoomRepository roomRepository;

    // ---------------- helper ----------------

    private UserDetailsImpl hostDetails(int hostId) {
        User u = new User();
        u.setId(hostId);
        u.setEmail("host@example.com");
        u.setPassword("x");
        return new UserDetailsImpl(u, List.of(new SimpleGrantedAuthority("ROLE_HOST")));
    }

    /** @AuthenticationPrincipal 解決用：SecurityContext に直接 Authentication を積む */
    private void setHostAuth(int hostId) {
        var principal = hostDetails(hostId);
        var auth = new UsernamePasswordAuthenticationToken(principal, "pw", principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Room roomOwnedBy(int roomId, int ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        Room room = new Room();
        room.setId(roomId);
        room.setUser(owner);
        return room;
    }

    private Review review(int reviewId, Room room, int score, boolean publicVisible) {
        Review r = new Review();
        r.setId(reviewId);
        r.setRoom(room);
        r.setScore(score);
        r.setPublicVisible(publicVisible);
        return r;
    }

    // Thymeleafの _csrf EL を回避するためのダミービュー解決
    @TestConfiguration
    static class ViewConfig {
        @Bean
        InternalResourceViewResolver viewResolver() {
            InternalResourceViewResolver vr = new InternalResourceViewResolver();
            vr.setPrefix("/templates/");
            vr.setSuffix(".html");
            return vr;
        }
    }

    @BeforeEach
    void resetMocks() {
        Mockito.reset(reviewRepository, auditLogRepository, roomRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------- tests ----------------

    @Nested
    @DisplayName("GET /host/reviews (一覧 + 簡易フィルタ)")
    class Index {
        @Test
        @DisplayName("ホスト所有の部屋で絞り込んだページが表示される（ビューモデル最低限の確認）")
        void index_ok() throws Exception {
            int hostId = 99;
            setHostAuth(hostId); // 認証セット

            var rooms = List.of(roomOwnedBy(1, hostId), roomOwnedBy(2, hostId));
            given(roomRepository.findByUser_IdOrderByNameAsc(hostId)).willReturn(rooms);

            Page<Review> page = new PageImpl<>(
                List.of(review(10, rooms.get(0), 5, true)),
                PageRequest.of(0, 5),
                1
            );
            given(reviewRepository.findAll(
                ArgumentMatchers.<Specification<Review>>any(),
                ArgumentMatchers.any(Pageable.class)
            )).willReturn(page);

            mvc.perform(get("/host/reviews")
                    .queryParam("roomId", "1")
                    .queryParam("stars", "5")
                    .queryParam("onlyHidden", "false")
                    // ★ Thymeleaf の _csrf EL を満たすダミー属性
                    .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "dummy")))
               .andExpect(status().isOk())
               .andExpect(view().name("host/reviews/index"))
               .andExpect(model().attributeExists("page", "rooms", "roomId", "stars", "onlyHidden"));

            then(roomRepository).should(times(1)).findByUser_IdOrderByNameAsc(hostId);
            then(reviewRepository).should(times(1)).findAll(
                ArgumentMatchers.<Specification<Review>>any(),
                ArgumentMatchers.any(Pageable.class)
            );
        }
    }

    @Nested
    @DisplayName("POST /host/reviews/{id}/reply (ホスト返信)")
    class Reply {
        @Test
        @DisplayName("所有権OKなら返信保存→監査ログ→一覧へリダイレクト")
        void reply_ok() throws Exception {
            int hostId = 99;
            setHostAuth(hostId);

            var room = roomOwnedBy(1, hostId);
            var r = review(10, room, 4, true);
            given(reviewRepository.findById(10)).willReturn(Optional.of(r));

            mvc.perform(post("/host/reviews/{id}/reply", 10)
                    .param("hostReply", "ありがとうございます！"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/host/reviews"))
               .andExpect(flash().attribute("message", "返信を保存しました。"));

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            then(reviewRepository).should(times(1)).save(captor.capture());
            Review saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(10);
            assertThat(saved.getHostReply()).isEqualTo("ありがとうございます！");
            assertThat(saved.getHostReplyAt()).isNotNull();

            then(auditLogRepository).should(times(1))
                .add(eq("host_reply"), eq(hostId), eq("review"), eq(10));
        }

        @Test
        @DisplayName("対象レビューが存在しなければ404")
        void reply_notFound() throws Exception {
            setHostAuth(1);
            given(reviewRepository.findById(999)).willReturn(Optional.empty());

            mvc.perform(post("/host/reviews/{id}/reply", 999)
                    .param("hostReply", "x"))
               .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("他人のレビューなら403")
        void reply_forbidden() throws Exception {
            int hostId = 99;
            setHostAuth(hostId);

            var otherRoom = roomOwnedBy(1, 777);
            var r = review(10, otherRoom, 3, true);
            given(reviewRepository.findById(10)).willReturn(Optional.of(r));

            mvc.perform(post("/host/reviews/{id}/reply", 10)
                    .param("hostReply", "x"))
               .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /host/reviews/{id}/visibility (公開/非公開切替)")
    class Toggle {
        @Test
        @DisplayName("公開ON：publicVisible=true, hiddenReason=null, 監査ログ=review_public_on")
        void toggle_public_on() throws Exception {
            int hostId = 99;
            setHostAuth(hostId);

            var room = roomOwnedBy(1, hostId);
            var r = review(10, room, 5, false);
            r.setHiddenReason("以前の理由");
            given(reviewRepository.findById(10)).willReturn(Optional.of(r));

            mvc.perform(post("/host/reviews/{id}/visibility", 10)
                    .param("isPublic", "true"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/host/reviews"))
               .andExpect(flash().attribute("message", "公開に変更しました。"));

            ArgumentCaptor<Review> captor = ArgumentCaptor.forClass(Review.class);
            then(reviewRepository).should(times(1)).save(captor.capture());
            Review saved = captor.getValue();
            assertThat(Boolean.TRUE.equals(saved.getPublicVisible())).isTrue();
            assertThat(saved.getHiddenReason()).isNull();

            then(auditLogRepository).should(times(1))
                .add(eq("review_public_on"), eq(hostId), eq("review"), eq(10));
        }

        @Test
        @DisplayName("公開OFF：reasonが空白ならnull、文字があればtrimして保存、監査ログ=review_public_off")
        void toggle_public_off_with_reasonTrim() throws Exception {
            int hostId = 99;
            setHostAuth(hostId);

            var room = roomOwnedBy(1, hostId);
            var r = review(11, room, 2, true);
            given(reviewRepository.findById(11)).willReturn(Optional.of(r));

            // 1回目：空白→null
            mvc.perform(post("/host/reviews/{id}/visibility", 11)
                    .param("isPublic", "false")
                    .param("reason", "   "))
               .andExpect(status().is3xxRedirection());

            ArgumentCaptor<Review> c1 = ArgumentCaptor.forClass(Review.class);
            then(reviewRepository).should(times(1)).save(c1.capture());
            assertThat(Boolean.FALSE.equals(c1.getValue().getPublicVisible())).isTrue();
            assertThat(c1.getValue().getHiddenReason()).isNull();

            then(auditLogRepository).should(times(1))
                .add(eq("review_public_off"), eq(hostId), eq("review"), eq(11));

            // 呼び出し履歴リセット & 再スタブ
            Mockito.reset(reviewRepository, auditLogRepository);
            given(reviewRepository.findById(11)).willReturn(Optional.of(r));

            // 2回目：trimして保存
            mvc.perform(post("/host/reviews/{id}/visibility", 11)
                    .param("isPublic", "false")
                    .param("reason", "  スパム対策  "))
               .andExpect(status().is3xxRedirection());

            ArgumentCaptor<Review> c2 = ArgumentCaptor.forClass(Review.class);
            then(reviewRepository).should(times(1)).save(c2.capture());
            assertThat(c2.getValue().getHiddenReason()).isEqualTo("スパム対策");
        }

        @Test
        @DisplayName("他人のレビューなら403 / 存在しないなら404")
        void toggle_forbidden_and_notfound() throws Exception {
            // 404
            setHostAuth(1);
            given(reviewRepository.findById(999)).willReturn(Optional.empty());
            mvc.perform(post("/host/reviews/{id}/visibility", 999)
                    .param("isPublic", "true"))
               .andExpect(status().isNotFound());

            // 403
            var others = review(12, roomOwnedBy(1, 555), 1, true);
            given(reviewRepository.findById(12)).willReturn(Optional.of(others));
            mvc.perform(post("/host/reviews/{id}/visibility", 12)
                    .param("isPublic", "false"))
               .andExpect(status().isForbidden());
        }
    }
}



