package com.example.studio_book.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.studio_book.entity.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    @Query("select count(ur)>0 from UserRole ur where ur.user.id = :userId and ur.role.id = :roleId")
    boolean existsByUserIdAndRoleId(@Param("userId") Integer userId,
                                    @Param("roleId") Integer roleId);
}
