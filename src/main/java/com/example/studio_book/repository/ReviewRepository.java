package com.example.studio_book.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.studio_book.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Integer>,
                                          JpaSpecificationExecutor<Review> {

    // 二重投稿防止（部屋×ユーザーでユニーク）
    boolean existsByRoom_IdAndUser_Id(Integer roomId, Integer userId);

    // 部屋ごと（公開/非公開を問わず）
    Page<Review> findByRoom_IdOrderByCreatedAtDesc(Integer roomId, Pageable pageable);

    // ホスト（= Room.user.id）横断一覧
    Page<Review> findByRoom_User_IdOrderByCreatedAtDesc(Integer hostUserId, Pageable pageable);

    // ====== 公開のみ ======

    // ★部屋ごとの「公開のみ」ページング（コントローラはコレを呼びます）
    Page<Review> findByRoomIdAndPublicVisibleTrueOrderByCreatedAtDesc(Integer roomId, Pageable pageable);

    // ★「非公開」かつ「ホスト返信あり」を別枠で表示
    List<Review> findByRoomIdAndPublicVisibleFalseAndHostReplyIsNotNullOrderByHostReplyAtDesc(Integer roomId);

    // ★平均（公開のみ／単一room）
    @Query("select avg(r.score) from Review r where r.room.id = :roomId and r.publicVisible = true")
    Double findAveragePublicScoreByRoomId(@Param("roomId") Integer roomId);

    // ★件数（公開のみ／単一room）
    long countByRoomIdAndPublicVisibleTrue(Integer roomId);

    // ★平均（公開のみ／複数room、一覧向け）
    @Query("select r.room.id, avg(r.score) "
         + "from Review r "
         + "where r.room.id in :roomIds and r.publicVisible = true "
         + "group by r.room.id")
    List<Object[]> findAveragePublicScoreByRoomIds(@Param("roomIds") Collection<Integer> roomIds);

    // ====== 参考：全件系（必要なら残す） ======

    // 平均（全件）
    @Query("select avg(r.score) from Review r where r.room.id = :roomId")
    Double getAverageScore(@Param("roomId") Integer roomId);

    // 件数（全件）
    @Query("select count(r) from Review r where r.room.id = :roomId")
    long countByRoomId(@Param("roomId") Integer roomId);

    // 複数roomの平均（全件）
    @Query("SELECT r.room.id, AVG(r.score) FROM Review r GROUP BY r.room.id")
    List<Object[]> findAverageScoresGroupedByRoom();

    // 指定 roomIds の平均（全件）
    @Query("SELECT r.room.id, AVG(r.score) FROM Review r WHERE r.room.id IN :roomIds GROUP BY r.room.id")
    List<Object[]> findAverageScoresByRoomIds(@Param("roomIds") Collection<Integer> roomIds);

    // 指定 roomIds の件数（全件）
    @Query("SELECT r.room.id, COUNT(r) FROM Review r WHERE r.room.id IN :roomIds GROUP BY r.room.id")
    List<Object[]> countByRoomIds(@Param("roomIds") Collection<Integer> roomIds);
    
    @Query("""
    	    SELECT r.room.id, AVG(r.score)
    	    FROM Review r
    	    WHERE r.publicVisible = true AND r.room.id IN :ids
    	    GROUP BY r.room.id
    	""")
    	List<Object[]> findAveragePublicScoresByRoomIds(@Param("ids") Collection<Integer> ids);

    	@Query("""
    	    SELECT r.room.id, COUNT(r)
    	    FROM Review r
    	    WHERE r.publicVisible = true AND r.room.id IN :ids
    	    GROUP BY r.room.id
    	""")
    	List<Object[]> countPublicByRoomIds(@Param("ids") Collection<Integer> ids);
    
    	  /** ▼▼ 新規：ルーム集合に対する全件平均（is_public 無視） */
        @Query("select avg(r.score) from Review r where r.room.id in :roomIds")
        Double averageScoreAcrossRooms(@Param("roomIds") Collection<Integer> roomIds);

        /** ▼▼ 新規：ルーム集合に対する公開のみ平均（is_public = true） */
        @Query("select avg(r.score) from Review r where r.publicVisible = true and r.room.id in :roomIds")
        Double averagePublicScoreAcrossRooms(@Param("roomIds") Collection<Integer> roomIds);

    	

}


