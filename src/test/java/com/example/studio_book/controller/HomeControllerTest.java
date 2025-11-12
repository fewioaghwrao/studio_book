// src/test/java/com/example/studio_book/controller/HomeControllerTest.java
package com.example.studio_book.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.entity.Room;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.service.RoomService;

@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false) // セキュリティフィルタ無効化（/ に認証不要なら付けると楽）
class HomeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    RoomService roomService;

    @MockBean
    ReviewRepository reviewRepository;

    private Room room(int id, String name) {
        Room r = new Room();
        r.setId(id);
        r.setName(name);
        // 必要なら createdAt / reservationCount 等もセット
        return r;
    }

    @Test
    @DisplayName("index: 新着・人気・レビュー集計がモデルに載り、view=index を返す")
    void index_ok() throws Exception {
        // --- 準備: 新着8件・人気3件 ---
        List<Room> newRooms = Arrays.asList(
                room(1, "A"), room(2, "B"), room(3, "C"),
                room(4, "D"), room(5, "E"), room(6, "F"),
                room(7, "G"), room(8, "H")
        );
        List<Room> popularRooms = Arrays.asList(room(2, "B"), room(9, "I"), room(10, "J"));

        given(roomService.findTop8RoomsByOrderByCreatedAtDesc()).willReturn(newRooms);
        given(roomService.findTop3RoomsByOrderByReservationCountDesc()).willReturn(popularRooms);

        // 集計対象のIDは {1..8, 9, 10}（2 は重複するが Set でユニーク化）
        Set<Integer> targetIds = new HashSet<>(Arrays.asList(1,2,3,4,5,6,7,8,9,10));

        // --- 準備: ReviewRepository の集計スタブ ---
        // 平均スコア: Object[] = {roomId(Integer), avg(Double)}
        List<Object[]> avgRows = Arrays.asList(
                new Object[]{2, 4.5d},
                new Object[]{9, 3.0d}
        );
        // 件数: Object[] = {roomId(Integer), count(Long)}
        List<Object[]> cntRows = Arrays.asList(
                new Object[]{2, 3L},
                new Object[]{9, 1L}
        );

        given(reviewRepository.findAveragePublicScoresByRoomIds(anySet())).willReturn(avgRows);
        given(reviewRepository.countPublicByRoomIds(anySet())).willReturn(cntRows);

        // --- 実行 & 検証 ---
        mockMvc.perform(get("/"))
               .andExpect(status().isOk())
               .andExpect(view().name("index"))
               // newRooms / popularRooms がモデルに乗る
               .andExpect(model().attribute("newRooms", newRooms))
               .andExpect(model().attribute("popularRooms", popularRooms))
               // avgScoreMap / reviewCountMap が期待値を含む（他IDは未集計でもよい）
               .andExpect(model().attribute("avgScoreMap", allOf(
                       instanceOf(Map.class),
                       hasEntry(2, 4.5d),
                       hasEntry(9, 3.0d)
               )))
               .andExpect(model().attribute("reviewCountMap", allOf(
                       instanceOf(Map.class),
                       hasEntry(2, 3L),
                       hasEntry(9, 1L)
               )));

        // Repository に渡されたID集合が「新着 ∪ 人気」と一致することを検証
        ArgumentCaptor<Set<Integer>> idsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(reviewRepository).findAveragePublicScoresByRoomIds(idsCaptor.capture());
        Set<Integer> passedForAvg = idsCaptor.getValue();
        verify(reviewRepository).countPublicByRoomIds(idsCaptor.capture());
        Set<Integer> passedForCnt = idsCaptor.getValue();

        // equals でもよいが、順序に依存しないよう containsAll/size を使う
        org.junit.jupiter.api.Assertions.assertTrue(passedForAvg.containsAll(targetIds));
        org.junit.jupiter.api.Assertions.assertEquals(targetIds.size(), passedForAvg.size());
        org.junit.jupiter.api.Assertions.assertTrue(passedForCnt.containsAll(targetIds));
        org.junit.jupiter.api.Assertions.assertEquals(targetIds.size(), passedForCnt.size());
    }

    @Test
    @DisplayName("index: ルームが空でも 200 & index & 空Map を返す")
    void index_emptyLists_ok() throws Exception {
        given(roomService.findTop8RoomsByOrderByCreatedAtDesc()).willReturn(new ArrayList<>());
        given(roomService.findTop3RoomsByOrderByReservationCountDesc()).willReturn(new ArrayList<>());

        // 空集合が渡る想定 → Repository は空結果を返す
        given(reviewRepository.findAveragePublicScoresByRoomIds(anySet())).willReturn(List.of());
        given(reviewRepository.countPublicByRoomIds(anySet())).willReturn(List.of());

        mockMvc.perform(get("/"))
               .andExpect(status().isOk())
               .andExpect(view().name("index"))
               .andExpect(model().attribute("newRooms", hasSize(0)))
               .andExpect(model().attribute("popularRooms", hasSize(0)))
               .andExpect(model().attribute("avgScoreMap", anEmptyMap()))
               .andExpect(model().attribute("reviewCountMap", anEmptyMap()));
    }
}
