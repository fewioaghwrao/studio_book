// src/test/java/com/example/studio_book/controller/HostSalesControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.example.studio_book.dto.HostSalesHead;            // ← 実プロジェクトのパッケージに合わせて
import com.example.studio_book.dto.HostSalesRowProjection;  // ← 実プロジェクトのパッケージに合わせて
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.ReservationChargeItemRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.security.UserDetailsImpl;

/**
 * - Thymeleaf自動設定は外す（テンプレ評価回避）
 * - testViewResolver を別名で登録（defaultViewResolver と衝突回避）
 * - Securityフィルタは有効のまま、.with(user(details)) で @AuthenticationPrincipal を満たす
 */
@WebMvcTest(controllers = HostSalesController.class,
        excludeAutoConfiguration = ThymeleafAutoConfiguration.class)
@Import(HostSalesControllerTest.TestConfig.class)
class HostSalesControllerTest {

    @Autowired MockMvc mvc;

    @MockBean ReservationRepository reservationRepository;
    @MockBean ReservationChargeItemRepository chargeItemRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean(name = "testViewResolver")
        ViewResolver testViewResolver() {
            var r = new InternalResourceViewResolver();
            r.setPrefix("/WEB-INF/views/");
            r.setSuffix(".jsp");
            r.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
            return r;
        }
        // 完全に描画を回避したい場合は以下でもOK
        // @Bean(name = "testViewResolver")
        // ViewResolver testViewResolver() {
        //     return (viewName, locale) -> (model, req, res) -> {};
        // }
    }

    /** principal に載せる UserDetailsImpl を実体で作成（ROLE_HOST 付与） */
    private UserDetailsImpl detailsOf(int userId) {
        User u = new User();
        u.setId(userId);
        u.setEnabled(true);
        // テスト用にemail未設定でもOK（UserDetailsImpl#getUsername がフォールバック）
        return new UserDetailsImpl(u, List.of(new SimpleGrantedAuthority("ROLE_HOST")));
    }

    @Nested
    @DisplayName("GET /host/sales_details")
    class Index {

        @Test
        @DisplayName("roomIdなし & onlyWithItems=true（デフォルト）")
        void index_default() throws Exception {
            int hostId = 11;

            given(reservationRepository.findRoomOptionsForHost(hostId))
                    .willReturn(Collections.emptyList());

            HostSalesRowProjection row = mock(HostSalesRowProjection.class);
            Page<HostSalesRowProjection> page = new PageImpl<>(List.of(row));

            // onlyWithItems=true → only=1, roomId=null
            given(reservationRepository.findSalesDetailsForHost(eq(hostId), eq(1), isNull(), any(Pageable.class)))
                    .willReturn(page);

            mvc.perform(get("/host/sales_details")
                    .with(user(detailsOf(hostId))))
                .andExpect(status().isOk())
                .andExpect(view().name("host/sales_details/index"))
                .andExpect(model().attributeExists("rows", "page", "roomOptions", "onlyWithItems")) // ← selectedRoomIdは除外
                .andExpect(model().attribute("selectedRoomId", org.hamcrest.Matchers.nullValue()))   // ← nullを明示検証
                .andExpect(model().attribute("onlyWithItems", true));

            // Pageable (@PageableDefault) の反映確認
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            then(reservationRepository).should()
                    .findSalesDetailsForHost(eq(hostId), eq(1), isNull(), captor.capture());
            Pageable p = captor.getValue();
            Assertions.assertThat(p.getPageSize()).isEqualTo(5);
            Assertions.assertThat(p.getSort().toString()).containsIgnoringCase("startAt: DESC");
        }

        @Test
        @DisplayName("roomId指定 & onlyWithItems=false")
        void index_withFilters() throws Exception {
            int hostId = 22;
            int roomId = 777;

            given(reservationRepository.findRoomOptionsForHost(hostId))
                    .willReturn(Collections.emptyList());

            HostSalesRowProjection row = mock(HostSalesRowProjection.class);
            Page<HostSalesRowProjection> page = new PageImpl<>(List.of(row));

            // onlyWithItems=false → only=0
            given(reservationRepository.findSalesDetailsForHost(eq(hostId), eq(0), eq(roomId), any(Pageable.class)))
                    .willReturn(page);

            mvc.perform(get("/host/sales_details")
                            .param("roomId", String.valueOf(roomId))
                            .param("onlyWithItems", "false")
                            .with(user(detailsOf(hostId))))
                    .andExpect(status().isOk())
                    .andExpect(view().name("host/sales_details/index"))
                    .andExpect(model().attributeExists("rows", "page", "roomOptions", "selectedRoomId", "onlyWithItems"))
                    .andExpect(model().attribute("selectedRoomId", roomId))
                    .andExpect(model().attribute("onlyWithItems", false));

            then(reservationRepository).should()
                    .findSalesDetailsForHost(eq(hostId), eq(0), eq(roomId), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("GET /host/sales_details/{id}")
    class Detail {

        @Test
        @DisplayName("正常（ヘッダが取得できる）")
        void detail_ok() throws Exception {
            int hostId = 33;
            int reservationId = 9001;

            HostSalesHead head = mock(HostSalesHead.class);
            given(reservationRepository.findSalesHeadOne(hostId, reservationId))
                    .willReturn(Optional.of(head));

            given(chargeItemRepository.findByReservationIdOrderBySliceStartAsc(reservationId))
                    .willReturn(Collections.emptyList());

            mvc.perform(get("/host/sales_details/{id}", reservationId)
                            .with(user(detailsOf(hostId))))
                    .andExpect(status().isOk())
                    .andExpect(view().name("host/sales_details/detail"))
                    .andExpect(model().attributeExists("reservation", "items"));

            then(reservationRepository).should().findSalesHeadOne(hostId, reservationId);
            then(chargeItemRepository).should().findByReservationIdOrderBySliceStartAsc(reservationId);
        }

        @Test
        @DisplayName("見つからない → 404")
        void detail_404() throws Exception {
            int hostId = 44;
            int reservationId = 12345;

            given(reservationRepository.findSalesHeadOne(hostId, reservationId))
                    .willReturn(Optional.empty());

            mvc.perform(get("/host/sales_details/{id}", reservationId)
                            .with(user(detailsOf(hostId))))
                    .andExpect(status().isNotFound());

            then(reservationRepository).should().findSalesHeadOne(hostId, reservationId);
            then(chargeItemRepository).should(never())
            .findByReservationIdOrderBySliceStartAsc(eq(reservationId));
        }
    }
}

