// src/main/java/com/example/studio_book/controller/HostStatsApiController.java
package com.example.studio_book.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.studio_book.dto.MonthlySeriesResponse;      // ★ 追加
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.HostStatsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HostStatsApiController {

    private final RoomRepository roomRepository;
    private final ReviewRepository reviewRepository;
    private final HostStatsService statsService;

    @GetMapping("/host/stats/api")
    public Map<String, Object> getStats(
            @AuthenticationPrincipal UserDetailsImpl principal,
            Integer roomId // 0 or null = 全体
    ) {
        var hostId = principal.getUser().getId();

        // ★ HostStatsService は getSeries(hostId, roomIdOrNull) を提供
        Integer roomIdOrNull = (roomId == null || roomId == 0) ? null : roomId;
        MonthlySeriesResponse series = statsService.getSeries(hostId, roomIdOrNull);
        
        // ▼ 追加：稼働率（％）を labels と同一順で算出
        var utilizationPercents = statsService.computeUtilizationPercents(
                hostId, roomIdOrNull, series.labels()
        );
        

        // ▼ 平均レビュー（全件／公開のみ）
        Double avgAny;
        Double avgPublic;

        if (roomIdOrNull != null) {
            // 単一ルーム
            avgAny    = reviewRepository.getAverageScore(roomIdOrNull);
            avgPublic = reviewRepository.findAveragePublicScoreByRoomId(roomIdOrNull);
        } else {
            // 全体（ホスト配下の全ルーム）
            var roomIds = roomRepository.findIdsByHostId(hostId);   // ★ RoomRepository に定義が必要
            if (roomIds == null || roomIds.isEmpty()) {
                avgAny = avgPublic = null;
            } else {
                avgAny    = reviewRepository.averageScoreAcrossRooms(roomIds);        // ★ 追加済みメソッドを使用
                avgPublic = reviewRepository.averagePublicScoreAcrossRooms(roomIds);   // ★ 同上
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("labels", series.labels());
        res.put("booked", series.booked());
        res.put("paid",   series.paid());
        res.put("utilizationPercents", utilizationPercents); // ★ 追加
        res.put("reviewAvgAny",    avgAny);     // is_public 無視
        res.put("reviewAvgPublic", avgPublic);  // is_public = true
        return res;
    }
}
