// src/main/java/com/example/studio_book/service/ClosureService.java
package com.example.studio_book.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.studio_book.entity.Closure;
import com.example.studio_book.entity.Room;
import com.example.studio_book.repository.ClosureRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClosureService {

    private final ClosureRepository closureRepository;
    private final RoomRepository roomRepository;

    public Room getOwnedRoomOrThrow(Integer roomId, UserDetailsImpl principal) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));
        Integer ownerId = room.getUser() != null ? room.getUser().getId() : null;
        Integer loginUserId = principal.getUser().getId();

        if (!Objects.equals(ownerId, loginUserId)) {
            throw new SecurityException("You don't own this room");
        }
        return room;
    }

    public List<Closure> list(Integer roomId, UserDetailsImpl principal) {
        getOwnedRoomOrThrow(roomId, principal);
        return closureRepository.findByRoomIdOrderByStartAtAsc(roomId);
    }

    @Transactional
    public void createAllDay(Integer roomId, LocalDate startDate, LocalDate endDate,
                             String reason, UserDetailsImpl principal) {
        getOwnedRoomOrThrow(roomId, principal);

        
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("終了日は開始日以降を指定してください。");
        }

        // 任意: 期間の上限（例: 366日を超える長期ブロック禁止）
        if (startDate.plusDays(366).isBefore(endDate)) {
            throw new IllegalArgumentException("休館期間が長すぎます（365日以内にしてください）。");
        }
        
        // 終日：開始00:00、終了の翌日00:00（FullCalendarのallDayレンジと相性良い）
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();

        // 交差チェック
        boolean overlaps = closureRepository
            .existsByRoomIdAndEndAtGreaterThanAndStartAtLessThan(roomId, start, endExclusive);
        if (overlaps) {
            throw new IllegalStateException("既存の休館と期間が重複しています。");
        }

        Closure c = new Closure();
        c.setRoomId(roomId);
        c.setStartAt(start);
        c.setEndAt(endExclusive);
        c.setReason(reason);
        closureRepository.save(c);
    }

    @Transactional
    public void delete(Integer roomId, Integer closureId, UserDetailsImpl principal) {
        getOwnedRoomOrThrow(roomId, principal);
        Closure c = closureRepository.findById(closureId)
                .orElseThrow(() -> new IllegalArgumentException("Closure not found"));
        if (!c.getRoomId().equals(roomId)) {
            throw new SecurityException("Room mismatch");
        }
        closureRepository.delete(c);
    }
    
    @Transactional
    public void create(Integer roomId, LocalDateTime startAt, LocalDateTime endAt,
                       String reason, UserDetailsImpl principal) {
        getOwnedRoomOrThrow(roomId, principal);

        if (endAt.isBefore(startAt)) {
            throw new IllegalArgumentException("終了日時は開始日時以降を指定してください。");
        }

        // 期間上限（任意例：90日以内）
        if (startAt.plusDays(90).isBefore(endAt)) {
            throw new IllegalArgumentException("休館期間が長すぎます（90日以内）。");
        }

        // 重複チェック（[start, end) 交差）
        boolean overlaps = closureRepository
            .existsByRoomIdAndEndAtGreaterThanAndStartAtLessThan(roomId, startAt, endAt);
        if (overlaps) {
            throw new IllegalStateException("既存の休館と期間が重複しています。");
        }

        Closure c = new Closure();
        c.setRoomId(roomId);
        c.setStartAt(startAt);
        c.setEndAt(endAt);
        c.setReason(reason);
        closureRepository.save(c);
    }
}

