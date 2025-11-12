// src/main/java/com/example/studio_book/service/dto/HostStatsDto.java
package com.example.studio_book.service.dto;

import java.util.List;

public record HostStatsDto(
    List<String> labels,
    List<Long>   booked,
    List<Long>   paid
) {}
