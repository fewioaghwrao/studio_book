package com.example.studio_book.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.studio_book.entity.User;

public interface UserRepository extends JpaRepository<User, Integer> {
	public User findByEmail(String email);
	public Page<User> findByNameLikeOrFuriganaLike(String nameKeyword, String furiganaKeyword, Pageable pageable);
	
    // ✅ ホスト（ROLE_HOST）をすべて取得するメソッド
    @Query("SELECT u FROM User u WHERE u.role.id = 2 AND u.enabled = true")
    List<User> findAllHostsEnabled();
}
