// src/main/java/com/example/studio_book/service/AdminStatsService.java
package com.example.studio_book.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.studio_book.dto.AdminStatsApiDto;
import com.example.studio_book.dto.RoomOptionDto;
import com.example.studio_book.entity.Room;
import com.example.studio_book.repository.AdminStatsRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.repository.UserRepository; // 役割=HOST を取得する想定

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final AdminStatsRepository repo;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;         // 例: role='HOST' のユーザー一覧取得用
    private final HostStatsService hostStatsService;     // ★ 既存のまま利用

    public List<RoomOptionDto> loadRoomOptionsWithHost() {
        return repo.findRoomOptionsWithHost();
    }

    public AdminStatsApiDto buildDashboard(int roomIdOrZeroAll) {
        var dto = new AdminStatsApiDto();

        // 1) ユーザー数
        dto.setProviderCount(repo.countProviders());
        dto.setGeneralCount(repo.countGeneralUsers());

        // 2) 直近3か月のラベル
        List<String> labels = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = 2; i >= 0; i--) labels.add(now.minusMonths(i).toString());
        dto.setLabels(labels);

        // 3) FEE売上（見込み・確定）
        List<Long> bookedFee = new ArrayList<>();
        List<Long> paidFee   = new ArrayList<>();
        for (String ym : labels) {
            Map<String, Long> fee = repo.sumMonthlyPlatformFee(ym, roomIdOrZeroAll);
            bookedFee.add(fee.getOrDefault("booked", 0L));
            paidFee.add(fee.getOrDefault("paid",   0L));
        }
        dto.setBookedFee(bookedFee);
        dto.setPaidFee(paidFee);

        // 4) 稼働率（HostStatsService は変更せずに利用）
        dto.setUtilizationPercents(calcAdminUtil(labels, roomIdOrZeroAll));

        // 5) 直近7日予約件数
        var end   = LocalDate.now();
        var start = end.minusDays(6);
        var daily = repo.countDailyReservations(start, end, roomIdOrZeroAll);
        List<String> wl = new ArrayList<>();
        List<Integer> wc = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            wl.add(d.toString());
            wc.add(daily.getOrDefault(d, 0));
        }
        dto.setWeeklyLabels(wl);
        dto.setWeeklyCounts(wc);

        return dto;
    }

    /**
     * 管理者用 稼働率：roomId>0 ならそのルームの値（HostStatsService をそのまま利用）、
     * roomId=0 なら「全スタジオの単純平均」を返す（HostStatsService は変更しない方針）。
     */
    private List<Double> calcAdminUtil(List<String> labels, int roomIdOrZeroAll) {
        if (roomIdOrZeroAll > 0) {
            // ルーム指定 → HostStatsService を “roomId 指定” でそのまま使用（正確）
            return hostStatsService.computeUtilizationPercents(null, roomIdOrZeroAll, labels);
        }

        // 全体 → すべてのルームについて算出し、月ごとに単純平均
        List<Room> allRooms = roomRepository.findAll();
        if (allRooms.isEmpty()) {
            return labels.stream().map(l -> 0.0).toList();
        }

        // 初期化
        double[] sum = new double[labels.size()];
        int[]    cnt = new int[labels.size()];

        for (Room r : allRooms) {
            List<Double> util = hostStatsService.computeUtilizationPercents(null, r.getId(), labels);
            for (int i = 0; i < labels.size(); i++) {
                // null 安全
                double v = (util != null && i < util.size() && util.get(i) != null) ? util.get(i) : 0.0;
                sum[i] += v;
                cnt[i] += 1;
            }
        }

        List<Double> avg = new ArrayList<>(labels.size());
        for (int i = 0; i < labels.size(); i++) {
            avg.add(cnt[i] == 0 ? 0.0 : sum[i] / cnt[i]);
        }
        return avg;
    }
}
