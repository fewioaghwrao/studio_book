// src/main/java/com/example/studio_book/repository/AdminStatsRepository.java
package com.example.studio_book.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.studio_book.dto.RoomOptionDto;

public interface AdminStatsRepository {
    List<RoomOptionDto> findRoomOptionsWithHost();
    long countProviders();
    long countGeneralUsers();

    /** ym = "YYYY-MM", roomIdOrZeroAll=0なら全体 */
    Map<String, Long> sumMonthlyPlatformFee(String ym, int roomIdOrZeroAll);

    /** 指定期間の予約件数（日別） */
    Map<LocalDate, Integer> countDailyReservations(LocalDate from, LocalDate to, int roomIdOrZeroAll);
}

