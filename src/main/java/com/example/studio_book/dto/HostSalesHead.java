package com.example.studio_book.dto;

import java.time.LocalDateTime;

public interface HostSalesHead {
    Integer getReservationId();
    String  getRoomName();
    String  getGuestName();
    LocalDateTime getStartAt();
    LocalDateTime getEndAt();
    Integer getAmount();
    String  getStatus();
}

