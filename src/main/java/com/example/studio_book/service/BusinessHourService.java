// src/main/java/com/example/studio_book/service/BusinessHourService.java
package com.example.studio_book.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.studio_book.entity.RoomBusinessHour;
import com.example.studio_book.form.BusinessHourRowForm;
import com.example.studio_book.form.BusinessHoursForm;
import com.example.studio_book.repository.RoomBusinessHourRepository;
import com.example.studio_book.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BusinessHourService {

    private final RoomRepository roomRepository;
    private final RoomBusinessHourRepository bhRepository;

    /** 1..7 の空行を作る */
    public BusinessHoursForm loadOrDefault(Integer roomId) {
        var room = roomRepository.findById(roomId).orElseThrow();
        var existing = bhRepository.findByRoomIdOrderByDayIndexAsc(roomId);

        var form = new BusinessHoursForm();
        form.setRoomId(roomId);
        var rows = new ArrayList<BusinessHourRowForm>();

        if (existing.isEmpty()) {
            // デフォルト 09:00-18:00（営業）
            IntStream.rangeClosed(1, 7).forEach(i -> rows.add(BusinessHourRowForm.builder()
                .dayIndex(i)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(18, 0))
                .holiday(false)
                .build()));
        } else {
            existing.forEach(e -> rows.add(BusinessHourRowForm.builder()
                .dayIndex(e.getDayIndex())
                .startTime(e.isHoliday() ? null : e.getStartTime())
                .endTime(e.isHoliday() ? null : e.getEndTime())
                .holiday(e.isHoliday())
                .build()));
        }
        form.setRows(rows);
        return form;
    }

    @Transactional
    public void save(Integer roomId, BusinessHoursForm form) {
        if (!roomId.equals(form.getRoomId())) throw new IllegalArgumentException("roomId mismatch");
        var room = roomRepository.findById(roomId).orElseThrow();

        // 1..7 の行だけ受理、重複 dayIndex は最後を優先
        Map<Integer, BusinessHourRowForm> map = new TreeMap<>();
        for (var r : form.getRows()) {
            if (r.getDayIndex() == null) continue;
            if (r.getDayIndex() < 1 || r.getDayIndex() > 7) continue;

            // 休みでない場合は start<end をチェック
            if (!r.isHoliday()) {
                if (r.getStartTime() == null || r.getEndTime() == null) {
                    throw new IllegalArgumentException("営業日の開始/終了は必須です (dayIndex=" + r.getDayIndex() + ")");
                }
                if (!r.getStartTime().isBefore(r.getEndTime())) {
                    throw new IllegalArgumentException("開始は終了より前にしてください (dayIndex=" + r.getDayIndex() + ")");
                }
            } else {
                // 休みなら時間は無視
                r.setStartTime(null);
                r.setEndTime(null);
            }
            map.put(r.getDayIndex(), r);
        }

        // 既存取得→更新 or 作成
        for (int i = 1; i <= 7; i++) {
            final int day = i;   // ← これがポイント

            var row = map.getOrDefault(day, BusinessHourRowForm.builder()
                .dayIndex(day).holiday(true).startTime(null).endTime(null).build());
            var opt = bhRepository.findByRoomIdAndDayIndex(roomId, day);

            RoomBusinessHour e = opt.orElseGet(() -> RoomBusinessHour.builder()
                .room(room)
                .dayIndex(day)   // ← i ではなく day
                .build());

            e.setHoliday(row.isHoliday());
            e.setStartTime(row.isHoliday() ? null : row.getStartTime());
            e.setEndTime(row.isHoliday() ? null : row.getEndTime());
            bhRepository.save(e);
        }
    }
    
    @Transactional(readOnly = true)
    public boolean fitsWithinBusinessHours(int roomId, LocalDateTime start, LocalDateTime end) {
        // 基本チェック：開始 < 終了
        if (start == null || end == null || !start.isBefore(end)) return false;

        // 予約区間が跨ぐすべての「日」を1日ずつ評価
        LocalDate d = start.toLocalDate();
        final LocalDate last = end.minusNanos(1).toLocalDate(); // end がちょうど0時のケースも含めるため -1ns

        while (!d.isAfter(last)) {
            // その日における評価区間（半開区間 [segStart, segEnd)）
            LocalDateTime segStart = d.equals(start.toLocalDate()) ? start : d.atStartOfDay();
            LocalDateTime segEnd   = d.equals(end.toLocalDate())   ? end   : d.plusDays(1).atStartOfDay();

            // DB: 1=月 … 7=日 という前提（異なる場合は合わせてください）
            int dayIndex = d.getDayOfWeek().getValue();

            var opt = bhRepository.findByRoomIdAndDayIndex(roomId, dayIndex);
            if (opt.isEmpty()) return false; // 未設定は「営業なし」扱い

            RoomBusinessHour h = opt.get();
            if (Boolean.TRUE.equals(h.isHoliday())) return false; // 休日は不可

            // 営業時間が欠けていても不可
            if (h.getStartTime() == null || h.getEndTime() == null) return false;

            // 営業開始/終了の LocalDateTime 化
            // endTime=00:00 を「24:00（翌日0:00）」として扱いたい場合の特別処理
            var openAt  = d.atTime(h.getStartTime());
            var closeAt = h.getEndTime().equals(LocalTime.MIDNIGHT)
                    ? d.plusDays(1).atStartOfDay() // 24:00 扱い
                    : d.atTime(h.getEndTime());

            // 範囲の妥当性：開始<終了でない営業時間は不可
            if (!openAt.isBefore(closeAt)) return false;

            // その日の予約セグメントが営業時間に「完全内包」されているか
            // ※ 半開区間 [segStart, segEnd) が [openAt, closeAt] 内にあることを求める
            boolean contained =
                    ( !segStart.isBefore(openAt) ) && // segStart >= openAt
                    (  segEnd.compareTo(closeAt) <= 0 ); // segEnd <= closeAt（segEndは半開の上端）

            if (!contained) return false;

            d = d.plusDays(1);
        }
        return true;
    }

    
}

