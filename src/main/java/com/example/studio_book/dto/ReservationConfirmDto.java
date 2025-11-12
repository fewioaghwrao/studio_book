package com.example.studio_book.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReservationConfirmDto {
    private Integer roomId;
    private String roomName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Integer hourlyPrice;
    private Long hours;
    private Long amount;
    
    // ★ 追加
    private java.util.List<com.example.studio_book.viewmodel.ConfirmLineItem> items; // 内訳
    private Long subtotal;  // 基本+固定+加算の小計
    private Long tax;       // 税額
}

