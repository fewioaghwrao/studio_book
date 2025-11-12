package com.example.studio_book.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.studio_book.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    public Role findByName(String name);
}
