package com.example.studio_book.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.studio_book.dto.AdminReservationRow;
import com.example.studio_book.dto.HostReservationRow;
import com.example.studio_book.dto.HostSalesHead;
import com.example.studio_book.dto.HostSalesRowProjection;
import com.example.studio_book.dto.RoomOption;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.entity.User;

public interface ReservationRepository extends JpaRepository<Reservation, Integer> {
    public Page<Reservation> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    boolean existsByPaymentIntentId(String paymentIntentId);

    // ホストIDに紐づく予約一覧（新しい開始時刻順）
    @Query("""
    		   SELECT new com.example.studio_book.dto.HostReservationRow(
    		       r.id, rm.name, gu.name, r.startAt, r.endAt, r.amount, r.status
    		   )
    		   FROM Reservation r
    		     JOIN r.room rm
    		     JOIN rm.user host
    		     JOIN r.user gu
    		   WHERE host.id = :hostId
    		   ORDER BY r.startAt DESC
    		""")

    List<HostReservationRow> findListForHost(Integer hostId);
    
 // 追記：ホスト向け一覧（ページング対応）
    @Query(
        value = """
            SELECT new com.example.studio_book.dto.HostReservationRow(
                r.id, rm.name, gu.name, r.startAt, r.endAt, r.amount, r.status
            )
            FROM Reservation r
              JOIN r.room rm
              JOIN rm.user host
              JOIN r.user gu
            WHERE host.id = :hostId
            ORDER BY r.startAt DESC
            """,
        countQuery = """
            SELECT COUNT(r)
            FROM Reservation r
              JOIN r.room rm
              JOIN rm.user host
            WHERE host.id = :hostId
            """
    )
    Page<HostReservationRow> findPageForHost(@Param("hostId") Integer hostId, Pageable pageable);
    
    @Query(
    	    value = """
    	        SELECT new com.example.studio_book.dto.AdminReservationRow(
    	            r.id, rm.name, gu.name, host.name, r.startAt, r.endAt, r.amount, r.status
    	        )
    	        FROM Reservation r
    	          JOIN r.room rm
    	          JOIN rm.user host
    	          JOIN r.user gu
    	        ORDER BY r.id DESC
    	        """,
    	    countQuery = """
    	        SELECT COUNT(r)
    	        FROM Reservation r
    	        """
    	)
    	Page<AdminReservationRow> findAdminReservationPage(Pageable pageable);
    
    // 承認/キャンセル操作時の所有チェック（その予約がそのホストの部屋のものか）
    Optional<Reservation> findByIdAndRoom_User_Id(Integer reservationId, Integer hostId);
    
    List<Reservation> findByRoomIdInAndStatusAndStartAtLessThanAndEndAtGreaterThan(
    	    List<Integer> roomIds, String status, LocalDateTime endExclusive, LocalDateTime startExclusive);
    
    // キャンセル以外を対象にするなど必要なら status で絞る
    List<Reservation> findByRoomIdAndEndAtAfterAndStartAtBefore(Integer roomId, LocalDateTime start, LocalDateTime end);
    
    // スタジオ選択用（ホストが所有する部屋一覧）
    @Query("""
    	    select new com.example.studio_book.dto.RoomOption(rm.id, rm.name)
    	    from Room rm
    	    where rm.user.id = :hostId
    	    order by rm.name
    	""")
    	List<RoomOption> findRoomOptionsForHost(@Param("hostId") Integer hostId);

    // 売上詳細（ネイティブ + インターフェイス投影 + ページング）
    @Query(
    	    value = """
    	        SELECT
    	          r.id                AS reservationId,
    	          rm.name             AS roomName,
    	          rm.id               AS roomId,
    	          u.name              AS guestName,
    	          r.start_at          AS startAt,
    	          r.end_at            AS endAt,
    	          r.amount            AS amount,
    	          r.status            AS status
    	        FROM reservations r
    	        JOIN rooms rm ON rm.id = r.room_id
    	        JOIN users u  ON u.id  = r.user_id
    	        WHERE rm.user_id = :hostId     -- ★ 修正
    	          AND (:onlyWithItems = 0 OR EXISTS (
    	                 SELECT 1 FROM reservation_charge_items i
    	                 WHERE i.reservation_id = r.id
    	               ))
    	          AND (:roomId IS NULL OR rm.id = :roomId)
    	        ORDER BY r.start_at DESC
    	        """,
    	    countQuery = """
    	        SELECT COUNT(*)
    	        FROM reservations r
    	        JOIN rooms rm ON rm.id = r.room_id
    	        WHERE rm.user_id = :hostId     -- ★ 修正
    	          AND (:onlyWithItems = 0 OR EXISTS (
    	                 SELECT 1 FROM reservation_charge_items i
    	                 WHERE i.reservation_id = r.id
    	               ))
    	          AND (:roomId IS NULL OR rm.id = :roomId)
    	        """,
    	    nativeQuery = true
    	)
    	Page<HostSalesRowProjection> findSalesDetailsForHost(
    	    @Param("hostId") Integer hostId,
    	    @Param("onlyWithItems") int onlyWithItems,
    	    @Param("roomId") Integer roomIdOrNull,
    	    Pageable pageable
    	);
 // ReservationRepository に追加（native 版）
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
      WHERE rm.user_id = :hostId     -- ← ホスト本人
        AND r.id = :reservationId
      """, nativeQuery = true)
    java.util.Optional<HostSalesHead> findSalesHeadOne(
        @org.springframework.data.repository.query.Param("hostId") Integer hostId,
        @org.springframework.data.repository.query.Param("reservationId") Integer reservationId
    );
    

    @Query("""
      select case when count(r)>0 then true else false end
      from Reservation r
      where r.room.id = :roomId
        and r.status <> 'canceled'
        and r.startAt < :endAt
        and :startAt < r.endAt
    """)
    boolean existsOverlapping(int roomId, LocalDateTime startAt, LocalDateTime endAt);
    
    @Query(
    	    value = """
    	        SELECT new com.example.studio_book.dto.AdminReservationRow(
    	            r.id, rm.name, gu.name, host.name, r.startAt, r.endAt, r.amount, r.status
    	        )
    	        FROM Reservation r
    	          JOIN r.room rm
    	          JOIN rm.user host
    	          JOIN r.user gu
    	        WHERE (:reservationId IS NULL OR r.id = :reservationId)
    	          AND (:status IS NULL OR r.status = :status)
    	          AND (:startFrom IS NULL OR r.startAt >= :startFrom)
    	          AND (:startTo   IS NULL OR r.startAt <= :startTo)
    	          AND (
    	               :kw IS NULL
    	               OR LOWER(rm.name)   LIKE LOWER(CONCAT('%', :kw, '%'))
    	               OR LOWER(gu.name)   LIKE LOWER(CONCAT('%', :kw, '%'))
    	               OR LOWER(host.name) LIKE LOWER(CONCAT('%', :kw, '%'))
    	          )
    	        ORDER BY r.id DESC
    	        """,
    	    countQuery = """
    	        SELECT COUNT(r)
    	        FROM Reservation r
    	          JOIN r.room rm
    	          JOIN rm.user host
    	          JOIN r.user gu
    	        WHERE (:reservationId IS NULL OR r.id = :reservationId)
    	          AND (:status IS NULL OR r.status = :status)
    	          AND (:startFrom IS NULL OR r.startAt >= :startFrom)
    	          AND (:startTo   IS NULL OR r.startAt <= :startTo)
    	          AND (
    	               :kw IS NULL
    	               OR LOWER(rm.name)   LIKE LOWER(CONCAT('%', :kw, '%'))
    	               OR LOWER(gu.name)   LIKE LOWER(CONCAT('%', :kw, '%'))
    	               OR LOWER(host.name) LIKE LOWER(CONCAT('%', :kw, '%'))
    	          )
    	        """
    	)
    	Page<com.example.studio_book.dto.AdminReservationRow> findAdminReservationPageFiltered(
    	    @Param("kw") String kw,
    	    @Param("status") String status,
    	    @Param("startFrom") java.time.LocalDateTime startFrom,
    	    @Param("startTo") java.time.LocalDateTime startTo,
    	    @Param("reservationId") Integer reservationId,
    	    Pageable pageable
    	);
    
    @Query(
    	    value = """
    	        SELECT new com.example.studio_book.dto.HostReservationRow(
    	            r.id, rm.name, gu.name, r.startAt, r.endAt, r.amount, r.status
    	        )
    	        FROM Reservation r
    	          JOIN r.room rm
    	          JOIN rm.user host
    	          JOIN r.user gu
    	        WHERE host.id = :hostId
    	          AND (:reservationId IS NULL OR r.id = :reservationId)
    	          AND (:status IS NULL OR r.status = :status)
    	          AND (:startFrom IS NULL OR r.startAt >= :startFrom)
    	          AND (:startTo   IS NULL OR r.startAt <= :startTo)
    	          AND (:roomId IS NULL OR rm.id = :roomId)
    	          AND (
    	               :kw IS NULL
    	               OR LOWER(rm.name) LIKE LOWER(CONCAT('%', :kw, '%'))
    	               OR LOWER(gu.name) LIKE LOWER(CONCAT('%', :kw, '%'))
    	          )
    	        ORDER BY r.startAt DESC
    	        """,
    	    countQuery = """
    	        SELECT COUNT(r)
    	        FROM Reservation r
    	          JOIN r.room rm
    	          JOIN rm.user host
    	          JOIN r.user gu
    	        WHERE host.id = :hostId
    	          AND (:reservationId IS NULL OR r.id = :reservationId)
    	          AND (:status IS NULL OR r.status = :status)
    	          AND (:startFrom IS NULL OR r.startAt >= :startFrom)
    	          AND (:startTo   IS NULL OR r.startAt <= :startTo)
    	          AND (:roomId IS NULL OR rm.id = :roomId)
    	          AND (
    	               :kw IS NULL
    	               OR LOWER(rm.name) LIKE LOWER(CONCAT('%', :kw, '%'))
    	               OR LOWER(gu.name) LIKE LOWER(CONCAT('%', :kw, '%'))
    	          )
    	        """
    	)
    	Page<com.example.studio_book.dto.HostReservationRow> findPageForHostFiltered(
    	    @Param("hostId") Integer hostId,
    	    @Param("kw") String kw,
    	    @Param("status") String status,
    	    @Param("startFrom") java.time.LocalDateTime startFrom,
    	    @Param("startTo") java.time.LocalDateTime startTo,
    	    @Param("reservationId") Integer reservationId,
    	    @Param("roomId") Integer roomId,
    	    Pageable pageable
    	);

    
    
}
