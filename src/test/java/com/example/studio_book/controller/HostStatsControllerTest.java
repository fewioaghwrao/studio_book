// src/test/java/com/example/studio_book/controller/HostStatsControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.AbstractView;

import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.HostStatsService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebMvcTest(controllers = HostStatsController.class)
@AutoConfigureMockMvc
class HostStatsControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RoomRepository roomRepository;

    // Controller のコンストラクタ依存。index() では直接使っていないが、コンテキスト起動のために MockBean を用意。
    @MockBean
    HostStatsService hostStatsService;

    /** テンプレートを評価せずにレンダリングをスキップするためのダミー ViewResolver */
    @TestConfiguration
    static class NoOpViewResolverConfig {
        @Bean
        ViewResolver viewResolver() {
            return new ViewResolver() {
                @Override
                public View resolveViewName(String viewName, Locale locale) {
                    // 何もしないダミービュー（model の検証は MockMvc 側で行う）
                    return new AbstractView() {
                        @Override
                        protected void renderMergedOutputModel(
                                Map<String, Object> model,
                                HttpServletRequest request,
                                HttpServletResponse response) {
                            // no-op
                        }
                    };
                }
            };
        }
    }

    private UserDetailsImpl detailsOf(int userId) {
        User u = new User();
        // 最低限 ID が取れれば OK（HostStatsController は principal.getUser().getId() しか参照しない）
        u.setId(userId);
        return new UserDetailsImpl(u);
    }

    @Nested
    @DisplayName("GET /host/stats")
    class Index {

        @Test
        @DisplayName("ログイン済みホスト → rooms を取得して画面表示")
        void index_success() throws Exception {
            int hostId = 44;

            // Room はフィールド不問。モックで十分
            Room r1 = org.mockito.Mockito.mock(Room.class);
            Room r2 = org.mockito.Mockito.mock(Room.class);

            given(roomRepository.findAllByHost(hostId))
                .willReturn(List.of(r1, r2));

            mvc.perform(get("/host/stats").with(user(detailsOf(hostId))))
               .andExpect(status().isOk())
               .andExpect(view().name("host/stats/index"))
               .andExpect(model().attributeExists("rooms"))
               .andExpect(model().attribute("rooms", List.of(r1, r2)));

            then(roomRepository).should().findAllByHost(hostId);
        }

        @Test
        @DisplayName("部屋が0件でもエラーにしない（空リストを詰めて表示）")
        void index_emptyList_ok() throws Exception {
            int hostId = 9001;

            given(roomRepository.findAllByHost(hostId))
                .willReturn(List.of());

            var result = mvc.perform(get("/host/stats").with(user(detailsOf(hostId))))
               .andExpect(status().isOk())
               .andExpect(view().name("host/stats/index"))
               .andExpect(model().attributeExists("rooms"))
               .andReturn();

            @SuppressWarnings("unchecked")
            List<Room> rooms = (List<Room>) result.getModelAndView().getModel().get("rooms");
            assertThat(rooms).isEmpty();

            then(roomRepository).should().findAllByHost(hostId);
        }

        @Test
        @DisplayName("認証なし → 401")
        void index_unauthenticated_401() throws Exception {
            mvc.perform(get("/host/stats"))
               .andExpect(status().isUnauthorized());
        }

    }
}
