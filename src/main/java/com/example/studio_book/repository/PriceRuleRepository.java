// com.example.studio_book.repository.PriceRuleRepository
package com.example.studio_book.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.studio_book.entity.PriceRule;

public interface PriceRuleRepository extends JpaRepository<PriceRule, Integer> {

    List<PriceRule> findByRoomId(Integer roomId);

    // 一覧表示用（曜日→開始時刻→ID）
    List<PriceRule> findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(Integer roomId);
    

    // 追加：同一曜日の固定費が既にあるか（weekday は null=全て も許容）
    boolean existsByRoomIdAndRuleTypeAndWeekday(Integer roomId, String ruleType, Integer weekday);

    // バルクDELETE（JPAのdeleteBy～より高速・明示的）
    @Modifying
    @Query("delete from PriceRule p where p.roomId = :roomId")
    void bulkDeleteByRoomId(@Param("roomId") Integer roomId);
}
