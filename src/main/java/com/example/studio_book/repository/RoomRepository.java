package com.example.studio_book.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.studio_book.entity.Room;

public interface RoomRepository extends JpaRepository<Room, Integer> {

    @EntityGraph(attributePaths = "user")   // ← user を同時ロード
    List<Room> findAll();
    
    public Page<Room> findByNameLike(String keyword, Pageable pageable);
    Page<Room> findByUser_Id(Integer ownerId, Pageable pageable);

    Page<Room> findByUser_IdAndNameContainingIgnoreCase(Integer ownerId, String keyword, Pageable pageable);
    Optional<Room> findByIdAndUser_Id(Integer id, Integer ownerId); // ← 詳細/削除用（本人限定で取得）
    
    public Room findFirstByOrderByIdDesc();
    public Page<Room> findByNameLikeOrAddressLikeOrderByCreatedAtDesc(String nameKeyword, String addressKeyword, Pageable pageable);
    public Page<Room> findByNameLikeOrAddressLikeOrderByPriceAsc(String nameKeyword, String addressKeyword, Pageable pageable);
    public Page<Room> findByAddressLikeOrderByCreatedAtDesc(String area, Pageable pageable);
    public Page<Room> findByAddressLikeOrderByPriceAsc(String area, Pageable pageable);
    public Page<Room> findByPriceLessThanEqualOrderByCreatedAtDesc(Integer price, Pageable pageable);
    public Page<Room> findByPriceLessThanEqualOrderByPriceAsc(Integer price, Pageable pageable);
    public Page<Room> findAllByOrderByCreatedAtDesc(Pageable pageable);
    public Page<Room> findAllByOrderByPriceAsc(Pageable pageable);   
    public List<Room> findTop8ByOrderByCreatedAtDesc();
    List<Room> findByUser_IdOrderByNameAsc(Integer userId);
    
    @Query("SELECT h FROM Room h LEFT JOIN h.reservations r GROUP BY h.id ORDER BY COUNT(r) DESC")
    List<Room> findAllByOrderByReservationCountDesc(Pageable pageable);    
    
    @Query("""
            SELECT r FROM Room r
            WHERE r.user.id = :hostId
            ORDER BY r.id
            """)
        List<Room> findAllByHost(@Param("hostId") Integer hostId);
    
    @Query("select r.id from Room r where r.user.id = :hostId")
    List<Integer> findIdsByHostId(@Param("hostId") Integer hostId);
    
    boolean existsByNameAndAddress(String name, String address);

    // 更新時は自分自身を除外して重複チェック
    boolean existsByNameAndAddressAndIdNot(String name, String address, Integer id);

}
