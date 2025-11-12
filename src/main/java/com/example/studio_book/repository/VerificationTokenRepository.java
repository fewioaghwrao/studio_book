package com.example.studio_book.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.studio_book.entity.VerificationToken;

public interface VerificationTokenRepository extends JpaRepository< VerificationToken, Integer> {
    public VerificationToken findByToken(String token);
}
