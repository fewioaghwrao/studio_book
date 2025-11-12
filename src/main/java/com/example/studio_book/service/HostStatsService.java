// src/main/java/com/example/studio_book/service/HostStatsService.java
package com.example.studio_book.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.studio_book.dto.MonthlySeriesResponse;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.RoomBusinessHour;
import com.example.studio_book.repository.ClosureRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.repository.ReservationStatsRepository;
import com.example.studio_book.repository.RoomBusinessHourRepository;
import com.example.studio_book.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HostStatsService {

    private final RoomRepository roomRepository;
    private final RoomBusinessHourRepository businessHourRepository;
    private final ClosureRepository closureRepository;
    private final ReservationRepository reservationRepository;
    
    private final ReservationStatsRepository statsRepo;

    /** 直近3か月（当月含む）のラベルとfrom/toを返す */
    private static record Window(List<String> labels, LocalDateTime from, LocalDateTime to) {}

    private Window last3MonthsWindow() {
        YearMonth thisMonth = YearMonth.now();              // 例: 2025-10
        YearMonth m1 = thisMonth.minusMonths(2);            // 2025-08
        YearMonth m2 = thisMonth.minusMonths(1);            // 2025-09
        YearMonth m3 = thisMonth;                           // 2025-10

        List<String> labels = List.of(
            m1.toString(), m2.toString(), m3.toString()     // "YYYY-MM"
        );

        LocalDateTime from = m1.atDay(1).atStartOfDay();    // 含む
        LocalDateTime to   = LocalDate.now().plusDays(1).atStartOfDay(); // 排他上限：翌日0時
        return new Window(labels, from, to);
    }

    public MonthlySeriesResponse getSeries(Integer hostId, Integer roomIdOrNull) {
        Window w = last3MonthsWindow();
        Map<String, BigDecimal> bookedMap = new HashMap<>();
        Map<String, BigDecimal> paidMap   = new HashMap<>();

        List<Object[]> bookedRaw;
        List<Object[]> paidRaw;

        if (roomIdOrNull == null) {
            bookedRaw = statsRepo.sumBookedByMonthAll(hostId, w.from, w.to);
            paidRaw   = statsRepo.sumPaidByMonthAll(hostId, w.from, w.to);
        } else {
            bookedRaw = statsRepo.sumBookedByMonthRoom(roomIdOrNull, w.from, w.to);
            paidRaw   = statsRepo.sumPaidByMonthRoom(roomIdOrNull, w.from, w.to);
        }

        for (Object[] row : bookedRaw) {
            bookedMap.put((String) row[0], (row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString())));
        }
        for (Object[] row : paidRaw) {
            paidMap.put((String) row[0], (row[1] == null ? BigDecimal.ZERO : new BigDecimal(row[1].toString())));
        }

        List<BigDecimal> booked = new ArrayList<>();
        List<BigDecimal> paid   = new ArrayList<>();
        for (String ym : w.labels) {
            booked.add(bookedMap.getOrDefault(ym, BigDecimal.ZERO));
            paid.add(paidMap.getOrDefault(ym, BigDecimal.ZERO));
        }

        return new MonthlySeriesResponse(w.labels, booked, paid);
    }
    

    /** 直近3か月の labels(YYYY-MM) と同順で稼働率[%]を返す */
    public List<Double> computeUtilizationPercents(Integer hostId, Integer roomIdOrNull, List<String> ymLabels) {
        if (ymLabels == null || ymLabels.isEmpty()) return List.of();

     // 対象ルームID
        List<Integer> roomIds = (roomIdOrNull != null)
                ? List.of(roomIdOrNull)
                : roomRepository.findAllByHost(hostId).stream()
                    .map(Room::getId)
                    .toList();

        if (roomIds.isEmpty()) {
            return ymLabels.stream().map(l -> 0.0).toList();
        }

        // YearMonth に変換（labels は "YYYY-MM" 前提）
        List<YearMonth> months = ymLabels.stream().map(YearMonth::parse).toList();

        // 一括検索範囲（最初の月の月初〜最後の月の翌月初）
        LocalDateTime rangeStart = months.get(0).atDay(1).atStartOfDay();
        LocalDateTime rangeEnd   = months.get(months.size() - 1).plusMonths(1).atDay(1).atStartOfDay();

        // 必要データまとめてフェッチ
        var reservations = reservationRepository
                .findByRoomIdInAndStatusAndStartAtLessThanAndEndAtGreaterThan(
                        roomIds, "paid", rangeEnd, rangeStart);

        var closures = closureRepository
                .findByRoomIdInAndStartAtLessThanAndEndAtGreaterThan(
                        roomIds, rangeEnd, rangeStart);

        Map<Integer, List<RoomBusinessHour>> bhByRoom =
                businessHourRepository.findByRoomIdIn(roomIds).stream()
                        .collect(Collectors.groupingBy(bh -> bh.getRoom().getId()));

        List<Double> result = new ArrayList<>();
        for (YearMonth ym : months) {
            LocalDate first = ym.atDay(1);
            LocalDate last  = ym.atEndOfMonth();

            long openMinutesAll = 0L;
            long paidMinutesAll = 0L;

            for (Integer roomId : roomIds) {
                var bhs = bhByRoom.getOrDefault(roomId, List.of());
                var roomClosures = closures.stream()
                        .filter(c -> c.getRoomId().equals(roomId))
                        .toList();

                // 日毎のオープン区間（closures を差し引いたもの）
                Map<LocalDate, List<Interval>> openMap = new HashMap<>();
                for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                	

                    final LocalDate day = d;   // ★ 追加
                    
                    int w = d.getDayOfWeek().getValue(); // 1..7
                    var bhOpt = bhs.stream().filter(b -> Objects.equals(b.getDayIndex(), w)).findFirst();

                    if (bhOpt.isEmpty() || bhOpt.get().isHoliday()
                            || bhOpt.get().getStartTime() == null || bhOpt.get().getEndTime() == null) {
                        openMap.put(d, List.of());
                        continue;
                    }
                    var bh = bhOpt.get();
                    var openStart = LocalDateTime.of(d, bh.getStartTime());
                    var openEnd   = LocalDateTime.of(d, bh.getEndTime());

                    List<Interval> base = List.of(new Interval(openStart, openEnd));

                    List<Interval> dayClosures = roomClosures.stream()
                            .map(c -> new Interval(c.getStartAt(), c.getEndAt()))
                            .filter(iv -> iv.overlapsDay(day))   // ← day を使用
                            .toList();

                    openMap.put(d, subtractAll(base, dayClosures));
                }

                long openMinutes = openMap.values().stream()
                        .flatMap(List::stream)
                        .mapToLong(Interval::minutes)
                        .sum();
                openMinutesAll += openMinutes;

                // 予約（paid）の「営業時間との重なり分」を積算
                var paidRes = reservations.stream()
                        .filter(r -> r.getRoom().getId().equals(roomId))
                        .toList();

                long paidMinutes = 0L;
                for (Reservation r : paidRes) {
                    LocalDate sDay = r.getStartAt().toLocalDate();
                    LocalDate eDay = r.getEndAt().toLocalDate();
                    for (LocalDate d = sDay; !d.isAfter(eDay); d = d.plusDays(1)) {
                        var opens = openMap.getOrDefault(d, List.of());
                        if (opens.isEmpty()) continue;

                        LocalDateTime segStart = max(r.getStartAt(), d.atStartOfDay());
                        LocalDateTime segEnd   = min(r.getEndAt(), d.plusDays(1).atStartOfDay());
                        Interval rv = new Interval(segStart, segEnd);

                        for (Interval op : opens) {
                            Interval inter = op.intersection(rv);
                            if (inter != null) paidMinutes += inter.minutes();
                        }
                    }
                }
                paidMinutesAll += paidMinutes;
            }

            double rate = (openMinutesAll <= 0) ? 0.0 : (paidMinutesAll * 100.0 / openMinutesAll);
            result.add(rate);
        }

        return result;
    }

    // ========= ヘルパ =========

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b){ return a.isAfter(b) ? a : b; }
    private static LocalDateTime min(LocalDateTime a, LocalDateTime b){ return a.isBefore(b) ? a : b; }

    @lombok.Value
    static class Interval {
        LocalDateTime start;
        LocalDateTime end;

        long minutes() { return Duration.between(start, end).toMinutes(); }

        boolean overlaps(Interval other) {
            // [start, end) 同士の重なり
            return start.isBefore(other.end) && end.isAfter(other.start);
        }

        boolean overlapsDay(LocalDate d) {
            var ds = d.atStartOfDay();
            var de = d.plusDays(1).atStartOfDay();
            return start.isBefore(de) && end.isAfter(ds);
        }

        Interval intersection(Interval other) {
            LocalDateTime s = max(this.start, other.start);
            LocalDateTime e = min(this.end,   other.end);
            return s.isBefore(e) ? new Interval(s, e) : null;
        }
    }

    private static List<Interval> subtractAll(List<Interval> base, List<Interval> blocks) {
        List<Interval> cur = new ArrayList<>(base);
        for (Interval b : blocks) cur = subtractOne(cur, b);
        return cur;
    }
    private static List<Interval> subtractOne(List<Interval> src, Interval b) {
        List<Interval> out = new ArrayList<>();
        for (Interval a : src) {
            if (!a.overlaps(b)) { out.add(a); continue; }
            if (b.start.isAfter(a.start)) out.add(new Interval(a.start, b.start)); // 左残り
            if (b.end.isBefore(a.end))   out.add(new Interval(b.end,   a.end));   // 右残り
        }
        return out;
    }
    
}

