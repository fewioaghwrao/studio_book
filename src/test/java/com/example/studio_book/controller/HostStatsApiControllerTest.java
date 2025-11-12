// src/test/java/com/example/studio_book/controller/HostStatsApiControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.dto.MonthlySeriesResponse;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.HostStatsService;

@WebMvcTest(HostStatsApiController.class)
@AutoConfigureMockMvc
class HostStatsApiControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RoomRepository roomRepository;

    @MockBean
    ReviewRepository reviewRepository;

    @MockBean
    HostStatsService statsService;

    private UserDetailsImpl principalOf(int hostId) {
        User u = new User();
        u.setId(hostId);
        u.setName("Host" + hostId);
        return new UserDetailsImpl(u, List.of(new SimpleGrantedAuthority("ROLE_HOST")));
    }

    private MonthlySeriesResponse sampleSeries() {
        // ★ BigDecimal に合わせる
        return new MonthlySeriesResponse(
            List.of("2025-08", "2025-09", "2025-10"),
            List.of(BigDecimal.valueOf(10), BigDecimal.valueOf(12), BigDecimal.valueOf(8)),
            List.of(BigDecimal.valueOf(9),  BigDecimal.valueOf(11), BigDecimal.valueOf(7))
        );
    }

    @Nested
    @DisplayName("roomId が null または 0（= 全体）")
    class Overall {

        @Test
        @DisplayName("roomId パラメータ無し → 全体。ホストに部屋がある場合、平均の全体集計メソッドを使用")
        void overall_noParam_hasRooms() throws Exception {
            int hostId = 44;
            var principal = principalOf(hostId);

            var series = sampleSeries();
            var utilization = List.of(50.0, 66.7, 80.0);

            given(statsService.getSeries(eq(hostId), isNull())).willReturn(series);
            given(statsService.computeUtilizationPercents(eq(hostId), isNull(), eq(series.labels())))
                    .willReturn(utilization);

            var roomIds = List.of(101, 102);
            given(roomRepository.findIdsByHostId(hostId)).willReturn(roomIds);

            given(reviewRepository.averageScoreAcrossRooms(roomIds)).willReturn(4.12);
            given(reviewRepository.averagePublicScoreAcrossRooms(roomIds)).willReturn(4.00);

            var res = mvc.perform(get("/host/stats/api")
                            .with(user(principal)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    // labels
                    .andExpect(jsonPath("$.labels[0]").value("2025-08"))
                    .andExpect(jsonPath("$.labels[1]").value("2025-09"))
                    .andExpect(jsonPath("$.labels[2]").value("2025-10"))
                    // booked（BigDecimal だが JSON では number。整数なら 10 で比較OK）
                    .andExpect(jsonPath("$.booked[0]").value(10))
                    .andExpect(jsonPath("$.booked[1]").value(12))
                    .andExpect(jsonPath("$.booked[2]").value(8))
                    // paid
                    .andExpect(jsonPath("$.paid[0]").value(9))
                    .andExpect(jsonPath("$.paid[1]").value(11))
                    .andExpect(jsonPath("$.paid[2]").value(7))
                    // utilization
                    .andExpect(jsonPath("$.utilizationPercents[0]").value(50.0))
                    .andExpect(jsonPath("$.utilizationPercents[1]").value(66.7))
                    .andExpect(jsonPath("$.utilizationPercents[2]").value(80.0))
                    // review averages
                    .andExpect(jsonPath("$.reviewAvgAny").value(4.12))
                    .andExpect(jsonPath("$.reviewAvgPublic").value(4.00))
                    .andReturn();

            var body = res.getResponse().getContentAsString();
            assertThat(body).contains("labels").contains("booked").contains("paid");

            then(reviewRepository).should(never()).getAverageScore(anyInt());
            then(reviewRepository).should(never()).findAveragePublicScoreByRoomId(anyInt());
        }

        @Test
        @DisplayName("roomId=0 → 全体扱い。ホストに部屋が無い場合、平均は null（Jackson設定により doesNotExist か nullValue）")
        void overall_roomIdZero_noRooms() throws Exception {
            int hostId = 55;
            var principal = principalOf(hostId);

            var series = sampleSeries();
            var utilization = List.of(10.0, 20.0, 30.0);

            given(statsService.getSeries(eq(hostId), isNull())).willReturn(series);
            given(statsService.computeUtilizationPercents(eq(hostId), isNull(), eq(series.labels())))
                    .willReturn(utilization);

            // 部屋なし
            given(roomRepository.findIdsByHostId(hostId)).willReturn(List.of());

            mvc.perform(get("/host/stats/api")
                            .param("roomId", "0")
                            .with(user(principal)))
                    .andExpect(status().isOk())
                    // ▼ Jackson の設定で挙動が異なるため、どちらか一方を採用してください
                    // 1) nullを出力しない設定なら:
                    // .andExpect(jsonPath("$.reviewAvgAny").doesNotExist())
                    // .andExpect(jsonPath("$.reviewAvgPublic").doesNotExist());
                    // 2) nullを出力する設定なら:
                    .andExpect(jsonPath("$.reviewAvgAny", Matchers.nullValue()))
                    .andExpect(jsonPath("$.reviewAvgPublic", Matchers.nullValue()));

            then(reviewRepository).should(never()).averageScoreAcrossRooms(anyList());
            then(reviewRepository).should(never()).averagePublicScoreAcrossRooms(anyList());
        }
    }

    @Nested
    @DisplayName("roomId が単一指定（= ルーム別）")
    class PerRoom {

        @Test
        @DisplayName("roomId=123 → 単一ルームの平均を取得")
        void perRoom_specific() throws Exception {
            int hostId = 60;
            int roomId = 123;
            var principal = principalOf(hostId);

            var series = sampleSeries();
            var utilization = List.of(33.3, 40.0, 25.0);

            given(statsService.getSeries(eq(hostId), eq(roomId))).willReturn(series);
            given(statsService.computeUtilizationPercents(eq(hostId), eq(roomId), eq(series.labels())))
                    .willReturn(utilization);

            given(reviewRepository.getAverageScore(roomId)).willReturn(3.45);
            given(reviewRepository.findAveragePublicScoreByRoomId(roomId)).willReturn(3.20);

            mvc.perform(get("/host/stats/api")
                            .param("roomId", String.valueOf(roomId))
                            .with(user(principal)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.labels.length()").value(series.labels().size()))
                    .andExpect(jsonPath("$.utilizationPercents[0]").value(33.3))
                    .andExpect(jsonPath("$.reviewAvgAny").value(3.45))
                    .andExpect(jsonPath("$.reviewAvgPublic").value(3.20));

            then(roomRepository).should(never()).findIdsByHostId(anyInt());
            then(reviewRepository).should(never()).averageScoreAcrossRooms(anyList());
            then(reviewRepository).should(never()).averagePublicScoreAcrossRooms(anyList());
        }
    }
}

