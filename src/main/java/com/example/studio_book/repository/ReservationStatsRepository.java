// src/main/java/com/example/studio_book/repository/ReservationStatsRepository.java
package com.example.studio_book.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.example.studio_book.entity.Reservation;

public interface ReservationStatsRepository extends Repository<Reservation, Integer> {

	@Query(value = """
			  SELECT DATE_FORMAT(r.start_at, '%Y-%m') AS ym, COALESCE(SUM(r.amount), 0) AS amount
			    FROM reservations r
			    JOIN rooms rm ON r.room_id = rm.id
			   WHERE rm.user_id = :hostId
			     AND LOWER(r.status) IN ('booked','confirmed','paid')
			     AND r.start_at >= :from AND r.start_at < :toPlus1
			   GROUP BY ym
			   ORDER BY ym
			  """, nativeQuery = true)
			List<Object[]> sumBookedByMonthAll(@Param("hostId") Integer hostId,
			                                   @Param("from") LocalDateTime from,
			                                   @Param("toPlus1") LocalDateTime toPlus1);

			@Query(value = """
			  SELECT DATE_FORMAT(r.start_at, '%Y-%m') AS ym, COALESCE(SUM(r.amount), 0) AS amount
			    FROM reservations r
			   WHERE r.room_id = :roomId
			     AND LOWER(r.status) IN ('booked','confirmed','paid')
			     AND r.start_at >= :from AND r.start_at < :toPlus1
			   GROUP BY ym
			   ORDER BY ym
			  """, nativeQuery = true)
			List<Object[]> sumBookedByMonthRoom(@Param("roomId") Integer roomId,
			                                    @Param("from") LocalDateTime from,
			                                    @Param("toPlus1") LocalDateTime toPlus1);


    // 確定売上：status=paid を start_at の月で集計（ホスト全体）
		@Query(value = """
			    SELECT DATE_FORMAT(r.start_at, '%Y-%m') AS ym, COALESCE(SUM(r.amount), 0) AS amount
			      FROM reservations r
			      JOIN rooms rm ON r.room_id = rm.id
			     WHERE rm.user_id = :hostId
			       AND r.status = 'paid'
			       AND r.start_at BETWEEN :from AND :to
			     GROUP BY ym
			     ORDER BY ym
			    """, nativeQuery = true)
			List<Object[]> sumPaidByMonthAll(@Param("hostId") Integer hostId,
			                                 @Param("from") LocalDateTime from,
			                                 @Param("to") LocalDateTime to);

			@Query(value = """
			    SELECT DATE_FORMAT(r.start_at, '%Y-%m') AS ym, COALESCE(SUM(r.amount), 0) AS amount
			      FROM reservations r
			     WHERE r.room_id = :roomId
			       AND r.status = 'paid'
			       AND r.start_at BETWEEN :from AND :to
			     GROUP BY ym
			     ORDER BY ym
			    """, nativeQuery = true)
			List<Object[]> sumPaidByMonthRoom(@Param("roomId") Integer roomId,
			                                  @Param("from") LocalDateTime from,
			                                  @Param("to") LocalDateTime to);

}

