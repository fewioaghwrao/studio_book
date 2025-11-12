// src/main/java/com/example/studio_book/dto/AdminStatsApiDto.java
package com.example.studio_book.dto;

import java.util.List;

import lombok.Data;

@Data
public class AdminStatsApiDto {
    // ユーザー総数
    private long providerCount;
    private long generalCount;

    // 月次（直近3か月）
    private List<String> labels;     // ["2025-08","2025-09","2025-10"]
    private List<Long> bookedFee;    // 見込み（FEE合計）
    private List<Long> paidFee;      // 確定（FEE合計）
    private List<Double> utilizationPercents; // 稼働率（%）

    // 週間（直近7日）
    private List<String> weeklyLabels;  // ["2025-10-26", ...]
    private List<Integer> weeklyCounts; // [3,1,0,...]
}