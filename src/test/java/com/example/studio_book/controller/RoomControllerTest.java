// src/test/java/com/example/studio_book/controller/RoomControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import com.example.studio_book.entity.PriceRule;
import com.example.studio_book.entity.Review;
import com.example.studio_book.entity.Room;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomBusinessHourRepository;
import com.example.studio_book.service.RoomService;
import com.example.studio_book.viewmodel.PriceRuleViewModel;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(value = RoomController.class, properties = "spring.thymeleaf.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
class RoomControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean RoomService roomService;
    @MockBean ReviewRepository reviewRepository;
    @MockBean RoomBusinessHourRepository roomBusinessHourRepository;
    @MockBean PriceRuleRepository priceRuleRepository;

    // ------------------------------------------------------------
    // Thymeleaf を描画しないダミー ViewResolver（_csrf 例外対策）
    // ------------------------------------------------------------
    @TestConfiguration
    static class NoopViewResolverConfig {

        interface OrderedViewResolver extends ViewResolver, Ordered {}

        @Bean
        OrderedViewResolver viewResolver() {
            return new OrderedViewResolver() {

                @Override
                public int getOrder() {
                    return Ordered.HIGHEST_PRECEDENCE;
                }

                @Override
                public View resolveViewName(String viewName, Locale locale) {
                    // ★ redirect/forward は素通し → Spring に処理させる
                    if (viewName != null && (viewName.startsWith("redirect:") || viewName.startsWith("forward:"))) {
                        return null;
                    }

                    // ★ 通常ビューは「no-op」→ Thymeleaf 評価をバイパス
                    return new View() {
                        @Override public String getContentType() { return "text/html"; }

                        @Override
                        public void render(
                                Map<String, ?> model,
                                HttpServletRequest req,
                                HttpServletResponse res
                        ) {
                            // no-op
                        }
                    };
                }
            };
        }
    }

    // ========= helpers =========
    private Room room(int id, String name, String addr, int price) {
        Room r = new Room();
        r.setId(id);
        r.setName(name);
        r.setAddress(addr);
        r.setPrice(price);

        // ★ これが今回の主因
        r.setCapacity(8);                 // ← null だと "null * 20" で落ちる

        // ある程度埋めておくと他の式でも落ちにくい
        r.setImageName("room01.jpg");
        r.setDescription("dummy desc");
        r.setPostalCode("100-0001");
        r.setCreatedAt(java.sql.Timestamp.valueOf("2025-11-01 00:00:00"));

        // user は非 null 必須
        com.example.studio_book.entity.User u = new com.example.studio_book.entity.User();
        u.setId(1000);
        u.setName("host");
        r.setUser(u);

        return r;
    }

    private Review review(int id, int roomId, boolean publicVisible) {
        Review rv = new Review();
        rv.setId(id);
        rv.setPublicVisible(publicVisible);
        // 必要に応じて score / createdAt 等をセット
        return rv;
    }

    private PriceRule flatFee(Integer weekday, Integer startHour, Integer endHour, int yen) {
        PriceRule pr = new PriceRule();
        pr.setRuleType("flat_fee");
        pr.setWeekday(weekday);
        pr.setStartHour(startHour == null ? null : LocalTime.of(startHour, 0));
        pr.setEndHour(endHour == null ? null : LocalTime.of(endHour, 0));
        pr.setFlatFee(Integer.valueOf(yen));
        return pr;
    }

    private PriceRule multiplier(Integer weekday, Integer startHour, Integer endHour, String mul) {
        PriceRule pr = new PriceRule();
        pr.setRuleType("multiplier");
        pr.setWeekday(weekday);
        pr.setStartHour(startHour == null ? null : LocalTime.of(startHour, 0));
        pr.setEndHour(endHour == null ? null : LocalTime.of(endHour, 0));
        pr.setMultiplier(new BigDecimal(mul)); // 例: "0.25"
        return pr;
    }

    // ============================================================
    @Nested
    @DisplayName("GET /rooms (index)")
    class Index {

    	@Test
    	@DisplayName("keyword あり & order=priceAsc → サービス呼び分け + avgScoreMap 構築")
    	void index_keyword_priceAsc() throws Exception {
    	    String keyword = "渋谷";
    	    String order = "priceAsc";

    	    Room r1 = room(1, "A", "渋谷区", 3000);
    	    Room r2 = room(2, "B", "渋谷区", 2500);
    	    Page<Room> page = new PageImpl<>(
    	        List.of(r1, r2),
    	        PageRequest.of(0, 10, Sort.by("id").ascending()),
    	        2
    	    );

    	    given(roomService.findRoomsByNameLikeOrAddressLikeOrderByPriceAsc(eq(keyword), eq(keyword), any(Pageable.class)))
    	        .willReturn(page);

    	    List<Object[]> avgRows = java.util.Arrays.asList(
    	        new Object[]{ Integer.valueOf(1), Double.valueOf(4.5) },
    	        new Object[]{ Integer.valueOf(2), Double.valueOf(3.0) }
    	    );
    	    given(reviewRepository.findAveragePublicScoreByRoomIds(List.of(1, 2)))
    	        .willReturn(avgRows);

    	    // ★ ここがポイント：keyword/order を付ける
    	    mvc.perform(get("/rooms")
    	            .param("keyword", keyword)
    	            .param("order", order))
    	       .andExpect(status().isOk())
    	       .andExpect(view().name("rooms/index"))
    	       .andExpect(model().attributeExists("roomPage", "avgScoreMap"))
    	       .andExpect(model().attribute("keyword", keyword))
    	       .andExpect(model().attribute("order", order))
    	       // area / price は null のはず
    	       .andExpect(model().attribute("area", nullValue()))
    	       .andExpect(model().attribute("price", nullValue()));

    	    then(roomService).should().findRoomsByNameLikeOrAddressLikeOrderByPriceAsc(eq(keyword), eq(keyword), any(Pageable.class));
    	    then(reviewRepository).should().findAveragePublicScoreByRoomIds(List.of(1, 2));
    	}

        @Test
        @DisplayName("パラメータなし（order なし）→ 新着順 API が呼ばれる & モデル属性が入る")
        void index_default_latest() throws Exception {
            Room r1 = room(10, "X", "新宿区", 4000);
            Page<Room> page = new PageImpl<>(List.of(r1));

            // 平均スコアのスタブは [10] に合わせる
            List<Object[]> avgRows = new java.util.ArrayList<>();
            avgRows.add(new Object[]{ Integer.valueOf(10), Double.valueOf(5.0) });

            given(roomService.findAllRoomsByOrderByCreatedAtDesc(any(Pageable.class)))
                .willReturn(page);
            given(reviewRepository.findAveragePublicScoreByRoomIds(List.of(10)))
                .willReturn(avgRows);

            mvc.perform(get("/rooms"))
            .andExpect(status().isOk())
            .andExpect(view().name("rooms/index"))
            .andExpect(model().attributeExists("roomPage", "avgScoreMap"))
            .andExpect(model().attribute("keyword", nullValue()))
            .andExpect(model().attribute("area", nullValue()))
            .andExpect(model().attribute("price", nullValue()))
            .andExpect(model().attribute("order", nullValue()));

            then(roomService).should().findAllRoomsByOrderByCreatedAtDesc(any(Pageable.class));
            then(reviewRepository).should().findAveragePublicScoreByRoomIds(List.of(10));
        }
    }

    // ============================================================
    @Nested
    @DisplayName("GET /rooms/{id} (show)")
    class Show {

        @Test
        @DisplayName("存在しない → /rooms にリダイレクト & フラッシュメッセージ")
        void show_notFound_redirect() throws Exception {
            given(roomService.findRoomById(99)).willReturn(Optional.empty());

            mvc.perform(get("/rooms/{id}", 99))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/rooms"));
        }

        @Test
        @DisplayName("存在する → 公開レビュー・非公開(返信あり)・平均/件数・営業時間・料金ルールを詰めて表示")
        void show_success() throws Exception {
            int id = 5;
            Room room = room(id, "Studio-5", "港区", 5000);
            given(roomService.findRoomById(id)).willReturn(Optional.of(room));

            // 公開レビュー（ページング）
            Page<Review> reviewsPage = new PageImpl<>(
                List.of(review(1, id, true), review(2, id, true)),
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")),
                2
            );
            given(reviewRepository.findByRoomIdAndPublicVisibleTrueOrderByCreatedAtDesc(eq(id), any(PageRequest.class)))
                    .willReturn(reviewsPage);

            // 非公開だがホスト返信あり
            given(reviewRepository.findByRoomIdAndPublicVisibleFalseAndHostReplyIsNotNullOrderByHostReplyAtDesc(id))
                    .willReturn(List.of(review(100, id, false)));

            // 平均・件数（公開のみ）
            given(reviewRepository.findAveragePublicScoreByRoomId(id)).willReturn(4.2d);
            given(reviewRepository.countByRoomIdAndPublicVisibleTrue(id)).willReturn(12L);

            // 営業時間
            given(roomBusinessHourRepository.findByRoomIdOrderByDayIndexAsc(id))
                    .willReturn(List.of()); // 空でOK

            // 料金ルール（flat_fee と multiplier 混在）
            var rules = List.of(
                flatFee(null, null, null, 800),        // 全日固定費
                flatFee(1, 10, 12, 300),               // 月曜10-12の固定費
                multiplier(6, 18, 22, "0.25"),         // 土曜18-22: 25% 加算
                multiplier(null, null, null, "0.10")   // 全日: 10% 加算
            );
            given(priceRuleRepository.findByRoomId(id)).willReturn(rules);

            // ---- 実行 & 期待 ----
            MvcResult res = mvc.perform(get("/rooms/{id}", id))
               .andExpect(status().isOk())
               .andExpect(view().name("rooms/show"))
               .andExpect(model().attributeExists(
                       "room", "reservationInputForm",
                       "reviewsPage", "hiddenWithReply",
                       "avgScore", "reviewCount",
                       "businessHours", "flatFeeRules", "multiplierRules"
               ))
               .andReturn();

            // ---- モデル内容の検証 ----
            @SuppressWarnings("unchecked")
            List<PriceRule> flatFees =
                (List<PriceRule>) res.getModelAndView().getModel().get("flatFeeRules");

            @SuppressWarnings("unchecked")
            List<PriceRuleViewModel> multiplierVMs =
                (List<PriceRuleViewModel>) res.getModelAndView().getModel().get("multiplierRules");

            // flat_fee はそのまま（Integer）
            assertThat(flatFees).extracting(PriceRule::getFlatFee)
                                .containsExactlyInAnyOrder(Integer.valueOf(800), Integer.valueOf(300));

            // multiplier は 5000 × 0.10 = 500、5000 × 0.25 = 1250（四捨五入）
            // PriceRuleViewModel の getter 名が不明な場合は extracting("amount") が安全
            assertThat(multiplierVMs).extracting("amount")
                                     .containsExactlyInAnyOrder(new BigDecimal("500"), new BigDecimal("1250"));

            // 平均と件数の値
            Object avgScore = res.getModelAndView().getModel().get("avgScore");
            Object reviewCount = res.getModelAndView().getModel().get("reviewCount");
            assertThat(avgScore).isEqualTo(4.2d);
            assertThat(reviewCount).isEqualTo(12L);
        }
    }
}

