// src/main/java/com/example/studio_book/dto/MonthlySeriesResponse.java
package com.example.studio_book.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthlySeriesResponse(
    List<String> labels,          // 例: ["2025-08","2025-09","2025-10"]
    List<BigDecimal> booked,      // 見込み（棒）
    List<BigDecimal> paid         // 確定（折れ線）
) {}
