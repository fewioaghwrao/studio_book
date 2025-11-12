// src/main/java/com/example/studio_book/repository/ClosureRepository.java
package com.example.studio_book.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.studio_book.entity.Closure;

public interface ClosureRepository extends JpaRepository<Closure, Integer> {
    List<Closure> findByRoomIdOrderByStartAtAsc(Integer roomId);

    // 重複チェック（[start,end) 交差）
    boolean existsByRoomIdAndEndAtGreaterThanAndStartAtLessThan(
        Integer roomId, LocalDateTime start, LocalDateTime end);
    
    List<Closure> findByRoomIdInAndStartAtLessThanAndEndAtGreaterThan(
    	    List<Integer> roomIds, LocalDateTime endExclusive, LocalDateTime startExclusive);
    
    List<Closure> findByRoomIdAndEndAtAfterAndStartAtBefore(Integer roomId, LocalDateTime start, LocalDateTime end);
    
    @Query("""
    	    select case when count(c)>0 then true else false end
    	    from Closure c
    	    where c.room.id = :roomId
    	      and c.startAt < :endAt
    	      and :startAt < c.endAt
    	  """)
    	  boolean existsOverlapping(int roomId, LocalDateTime startAt, LocalDateTime endAt);
}

