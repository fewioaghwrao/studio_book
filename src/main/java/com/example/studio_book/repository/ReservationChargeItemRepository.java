package com.example.studio_book.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.studio_book.dto.HostSalesHead;
import com.example.studio_book.entity.ReservationChargeItem;

public interface ReservationChargeItemRepository
        extends JpaRepository<ReservationChargeItem, Integer> {

    List<ReservationChargeItem> findByReservationId(Integer reservationId);
    
    java.util.List<ReservationChargeItem>
    findByReservationIdOrderBySliceStartAsc(Integer reservationId);
    
 // 予約ヘッダ1件（ホスト本人＆予約ID一致）
    @Query(value = """
      SELECT
        r.id       AS reservationId,
        rm.name    AS roomName,
        u.name     AS guestName,
        r.start_at AS startAt,
        r.end_at   AS endAt,
        r.amount   AS amount,
        r.status   AS status
      FROM reservations r
      JOIN rooms rm ON rm.id = r.room_id
      JOIN users u  ON u.id  = r.user_id
      WHERE rm.user_id = :hostId
        AND r.id = :reservationId
      """, nativeQuery = true)
    Optional<HostSalesHead> findSalesHeadOne(@Param("hostId") Integer hostId,
                                             @Param("reservationId") Integer reservationId);
    
}
