// src/main/java/com/example/studio_book/repository/RoomBusinessHourRepository.java
package com.example.studio_book.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.studio_book.entity.RoomBusinessHour;

public interface RoomBusinessHourRepository extends JpaRepository<RoomBusinessHour, Integer> {
    List<RoomBusinessHour> findByRoomIdOrderByDayIndexAsc(Integer roomId);
    Optional<RoomBusinessHour> findByRoomIdAndDayIndex(Integer roomId, Integer dayIndex);
    List<RoomBusinessHour> findByRoomIdIn(List<Integer> roomIds);
}
