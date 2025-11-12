// src/test/java/com/example/studio_book/controller/AdminStatsControllerTest.java
package com.example.studio_book.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.dto.AdminStatsApiDto;
import com.example.studio_book.dto.RoomOptionDto;
import com.example.studio_book.service.AdminStatsService;

@WebMvcTest(AdminStatsController.class)
@AutoConfigureMockMvc(addFilters = false) // Spring Security のフィルタを無効化（必要に応じて外してください）
class AdminStatsControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    AdminStatsService service;

    @Test
    @DisplayName("GET /admin/stats: rooms をモデルに積んでビュー admin/stats/index を返す")
    void index_returnsViewAndRoomsInModel() throws Exception {
        // given
        List<RoomOptionDto> rooms = List.of(mock(RoomOptionDto.class));
        given(service.loadRoomOptionsWithHost()).willReturn(rooms);

        // when & then
        mvc.perform(get("/admin/stats"))
           .andExpect(status().isOk())
           .andExpect(view().name("admin/stats/index"))
           .andExpect(model().attributeExists("rooms"))
           // 同一インスタンスであることまで確認（equals未実装でもOK）
           .andExpect(model().attribute("rooms", sameInstance(rooms)));

        // serviceが呼ばれていること
        then(service).should().loadRoomOptionsWithHost();
    }

    @Test
    @DisplayName("GET /admin/stats/api: roomId未指定なら 0 を渡してJSONを返す")
    void api_withoutRoomId_callsServiceWithZero() throws Exception {
        // given
        AdminStatsApiDto dummy = new AdminStatsApiDto(); // 中身は不問。存在する型を返すだけ
        given(service.buildDashboard(anyInt())).willReturn(dummy);

        // when
        mvc.perform(get("/admin/stats/api"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        // then: 引数検証
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        then(service).should().buildDashboard(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("GET /admin/stats/api?roomId=123: 指定IDをそのまま渡してJSONを返す")
    void api_withRoomId_callsServiceWithGivenId() throws Exception {
        // given
        AdminStatsApiDto dummy = new AdminStatsApiDto();
        given(service.buildDashboard(anyInt())).willReturn(dummy);

        // when
        mvc.perform(get("/admin/stats/api").param("roomId", "123"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        // then
        ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        then(service).should().buildDashboard(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue()).isEqualTo(123);
    }
}
