// src/main/java/com/example/studio_book/controller/RoomCalendarController.java
package com.example.studio_book.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.studio_book.entity.Closure;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.entity.RoomBusinessHour;
import com.example.studio_book.repository.ClosureRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.repository.RoomBusinessHourRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/calendar")
public class RoomCalendarController {

    private final ClosureRepository closureRepo;
    private final ReservationRepository reservationRepo;
    private final RoomBusinessHourRepository bhRepo;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    record Interval(LocalDateTime start, LocalDateTime end) {}

    @GetMapping(value = "/events", produces = "application/json")
    public ResponseEntity<List<Map<String, Object>>> events(
            @PathVariable Integer roomId,
            // ★ LocalDateTime → OffsetDateTime に変更（+09:00 を正しく受ける）
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {

        // FullCalendar は [start, end)（end は排他端）
        final LocalDateTime startL = start.toLocalDateTime();
        final LocalDateTime endL   = end.toLocalDateTime();

        
        // ★ デバッグログ追加
        System.out.println("=== Calendar Events Request ===");
        System.out.println("Room ID: " + roomId);
        System.out.println("Start: " + startL + " | End: " + endL);
        
        // 1) マスタ
        var bhs = bhRepo.findByRoomIdOrderByDayIndexAsc(roomId);
        System.out.println("Business Hours Count: " + bhs.size()); // ← これを追加
        bhs.forEach(bh -> System.out.println("  DOW:" + bh.getDayIndex() + 
        	    " Holiday:" + bh.isHoliday() + 
        	    " " + bh.getStartTime() + "-" + bh.getEndTime())); // ← これを追加
        var bhByDow = bhs.stream().collect(Collectors.toMap(RoomBusinessHour::getDayIndex, x -> x));

        // 2) 閉鎖・予約（[startL, endL) で取得）
        var closures = closureRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(roomId, startL, endL);
        var reservations = reservationRepo.findByRoomIdAndEndAtAfterAndStartAtBefore(roomId, startL, endL);
        
        System.out.println("Closures: " + closures.size());
        System.out.println("Reservations: " + reservations.size());

        List<Map<String, Object>> events = new ArrayList<>();

        // (B) 休業（背景）
        for (Closure c : closures) {
            events.add(bgEvent("休業", c.getStartAt(), c.getEndAt(), "#e9ecef", "#ced4da", "closure"));
        }

        // (C) 予約（前面）
        for (Reservation r : reservations) {
            var e = fgEvent("予約済み", r.getStartAt(), r.getEndAt(), "#ff6b6b", "reservation");
            e.put("extendedProps", Map.of(
                "type", "reservation",
                "reservationId", r.getId(),
                "status", Optional.ofNullable(r.getStatus()).orElse("")
            ));
            events.add(e);
        }

        // (A) OPEN 背景を作る（日ごと）
        // ★ ループの範囲を確認
        int dayCount = 0;
        // ★ ここを [startL, endL) の“排他端”ループに
        for (LocalDate d = startL.toLocalDate(); d.isBefore(endL.toLocalDate()); d = d.plusDays(1)) {
        	   dayCount++;
            int dow = d.getDayOfWeek().getValue(); // 1..7
            var bh = bhByDow.get(dow);
            // ★ これを追加
            if (bh == null) {
                System.out.println("  " + d + " (DOW:" + dow + ") -> BH: NULL");
                continue;
            }
            if (bh.isHoliday()) {
                System.out.println("  " + d + " (DOW:" + dow + ") -> HOLIDAY");
                continue;
            }
            if (bh.getStartTime() == null || bh.getEndTime() == null) {
                System.out.println("  " + d + " (DOW:" + dow + ") -> NO TIME SET");
                continue;
            }
            if (bh == null || bh.isHoliday() || bh.getStartTime() == null || bh.getEndTime() == null) {
                continue;
            }
            // ★ ここまで到達しているか確認
            System.out.println("  " + d + " (DOW:" + dow + ") -> PROCESSING OPEN");
            var openStart = max(LocalDateTime.of(d, bh.getStartTime()), startL);
            var openEnd   = min(LocalDateTime.of(d, bh.getEndTime()),   endL);
            System.out.println("    openStart: " + openStart + " | openEnd: " + openEnd);
            
            if (!openStart.isBefore(openEnd)) {
                System.out.println("    -> SKIPPED (openStart >= openEnd)");
                continue;
            }

            final LocalDateTime dayOpenStart = openStart;
            final LocalDateTime dayOpenEnd   = openEnd;

            var dayClosures = closures.stream()
                .filter(c -> overlaps(dayOpenStart, dayOpenEnd, c.getStartAt(), c.getEndAt()))
                .map(c -> new Interval(max(dayOpenStart, c.getStartAt()), min(dayOpenEnd, c.getEndAt())))
                .toList();
            
            System.out.println("    dayClosures count: " + dayClosures.size());


            var openSegments = subtract(dayOpenStart, dayOpenEnd, dayClosures);
            System.out.println("    openSegments count: " + openSegments.size());
            for (Interval seg : openSegments) {
            	System.out.println("    -> Creating OPEN event: " + seg.start + " ~ " + seg.end);
                events.add(bgEvent("営業", seg.start, seg.end, "#e6ffe6", "#cde8cd", "open"));
            }
        }
        System.out.println("Processed Days: " + dayCount);
        System.out.println("Total Events: " + events.size());
        System.out.println("===============================");

        return ResponseEntity.ok(events);
    }


    /** 背景イベント */
    private Map<String, Object> bgEvent(String title, LocalDateTime s, LocalDateTime e,
                                        String bgColor, String borderColor, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("start", ISO.format(s));
        m.put("end", ISO.format(e));
        m.put("display", "background");
        m.put("backgroundColor", bgColor);
        m.put("borderColor", borderColor);
        m.put("extendedProps", Map.of("type", type));
        return m;
    }

    /** 前面イベント */
    private Map<String, Object> fgEvent(String title, LocalDateTime s, LocalDateTime e,
                                        String color, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("start", ISO.format(s));
        m.put("end", ISO.format(e));
        m.put("color", color);
        m.put("extendedProps", Map.of("type", type));
        return m;
    }

    private static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
                                    LocalDateTime bStart, LocalDateTime bEnd) {
        return aEnd.isAfter(bStart) && bEnd.isAfter(aStart);
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * [baseStart, baseEnd) から closures を差し引いて残る OPEN 区間を返す
     * 入力 closures は同日・クリップ済み前提
     */
    private static List<Interval> subtract(LocalDateTime baseStart, LocalDateTime baseEnd,
                                           List<Interval> closures) {
        // 閉鎖を開始時刻でソート
        List<Interval> cs = new ArrayList<>(closures);
        cs.sort(Comparator.comparing(i -> i.start));

        List<Interval> result = new ArrayList<>();
        LocalDateTime cursor = baseStart;

        for (Interval c : cs) {
            if (!cursor.isBefore(baseEnd)) break;
            // ギャップが OPEN
            if (c.start.isAfter(cursor)) {
                var openStart = cursor;
                var openEnd   = min(c.start, baseEnd);
                if (openStart.isBefore(openEnd)) {
                    result.add(new Interval(openStart, openEnd));
                }
            }
            // カーソルを閉鎖の後ろへ
            if (c.end.isAfter(cursor)) {
                cursor = min(c.end, baseEnd);
            }
        }
        // 末尾の OPEN
        if (cursor.isBefore(baseEnd)) {
            result.add(new Interval(cursor, baseEnd));
        }
        return result;
    }
}

