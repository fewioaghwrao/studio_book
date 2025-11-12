// src/main/java/com/example/studio_book/repository/impl/AdminStatsRepositoryImpl.java
package com.example.studio_book.repository.impl;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.example.studio_book.dto.RoomOptionDto;
import com.example.studio_book.repository.AdminStatsRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Repository
public class AdminStatsRepositoryImpl implements AdminStatsRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<RoomOptionDto> findRoomOptionsWithHost() {
        return em.createQuery("""
            select new com.example.studio_book.dto.RoomOptionDto(
                r.id,
                r.name,
                u.name
            )
            from Room r
            join r.user u
            order by r.name asc
            """, RoomOptionDto.class)
            .getResultList();
    }


    @Override
    public long countProviders() {
        return em.createQuery("""
            select count(distinct u)
            from User u
            join u.roles r
            where r.name in (:names)
            """, Long.class)
        .setParameter("names", List.of("ROLE_HOST", "HOST"))
        .getSingleResult();
    }

    @Override
    public long countGeneralUsers() {
        return em.createQuery("""
            select count(distinct u)
            from User u
            join u.roles r
            where r.name in (:names)
            """, Long.class)
        .setParameter("names", List.of("ROLE_GENERAL", "ROLE_USER", "USER", "GENERAL"))
        .getSingleResult();
    }

    @Override
    public Map<String, Long> sumMonthlyPlatformFee(String ym, int roomIdOrZeroAll) {
        YearMonth yearMonth = YearMonth.parse(ym);
        var start = yearMonth.atDay(1).atStartOfDay();
        var end   = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        String roomFilter = roomIdOrZeroAll == 0 ? "" : " and r.room.id = :roomId ";
        String feeFilter  = " and rci.kind in ('ADMIN_FEE','PLATFORM_FEE','FEE') ";

        // 見込み（booked）- Reservationのstatusとcreated_at基準
        String bookedSql = """
            select coalesce(sum(rci.sliceAmount), 0)
            from ReservationChargeItem rci
            join Reservation r on r.id = rci.reservationId
            where r.startAt >= :start and r.startAt < :end
              and r.status in ('booked','confirmed','paid')
              """ + feeFilter + roomFilter;
        
        var bookedQuery = em.createQuery(bookedSql, Long.class)
        	    .setParameter("start", start)   // ← そのまま LocalDateTime
        	    .setParameter("end",   end);    // ← そのまま LocalDateTime
        
        if (roomIdOrZeroAll != 0) {
            bookedQuery.setParameter("roomId", roomIdOrZeroAll);
        }
        
        Long booked = bookedQuery.getSingleResult();

        // 確定（paid）- status='paid'のみ
        String paidSql = """
            select coalesce(sum(rci.sliceAmount), 0)
            from ReservationChargeItem rci
            join Reservation r on r.id = rci.reservationId
            where r.status = 'paid'
              and r.startAt >= :start and r.startAt < :end
              """ + feeFilter + roomFilter;
        
        var paidQuery = em.createQuery(paidSql, Long.class)
        	    .setParameter("start", start)   // ← そのまま LocalDateTime
        	    .setParameter("end",   end);    // ← そのまま LocalDateTime
        
        if (roomIdOrZeroAll != 0) {
            paidQuery.setParameter("roomId", roomIdOrZeroAll);
        }
        
        Long paid = paidQuery.getSingleResult();

        Map<String, Long> map = new HashMap<>();
        map.put("booked", booked == null ? 0L : booked);
        map.put("paid", paid == null ? 0L : paid);
        return map;
    }

    @Override
    public Map<LocalDate, Integer> countDailyReservations(LocalDate from, LocalDate to, int roomIdOrZeroAll) {
        String roomFilter = roomIdOrZeroAll == 0 ? "" : " and r.room.id = :roomId ";

        String sql = """
                select function('DATE', r.startAt), count(r)
                from Reservation r
                where r.startAt >= :from and r.startAt < :toPlus1
                  and r.status in ('booked','confirmed','paid')
                  """ + roomFilter + """
                group by function('DATE', r.startAt)
                order by function('DATE', r.startAt)
                """;
        
        var query = em.createQuery(sql, Object[].class)
        		 .setParameter("from",   from.atStartOfDay())               // LocalDateTime
        		    .setParameter("toPlus1",to.plusDays(1).atStartOfDay());    // LocalDateTime
        
        if (roomIdOrZeroAll != 0) {
            query.setParameter("roomId", roomIdOrZeroAll);
        }
        
        var rows = query.getResultList();

        Map<LocalDate, Integer> map = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate d = ((java.sql.Date) row[0]).toLocalDate();
            Integer c = ((Number) row[1]).intValue();
            map.put(d, c);
        }
        return map;
    }
}

