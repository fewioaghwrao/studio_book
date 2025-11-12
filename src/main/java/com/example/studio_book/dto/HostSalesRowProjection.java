// src/main/java/com/example/studio_book/dto/HostSalesRowProjection.java
package com.example.studio_book.dto;

import java.time.LocalDateTime;

public interface HostSalesRowProjection {
    Integer getReservationId();
    String  getRoomName();
    Integer getRoomId();
    String  getGuestName();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    Integer getAmount();
    String  getStatus();
}
