// src/main/java/com/example/studio_book/dto/AdminReservationRow.java
package com.example.studio_book.dto;

import java.time.LocalDateTime;

public class AdminReservationRow {
    private final Integer reservationId;
    private final String roomName;
    private final String guestName;
    private final String hostName;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final Integer amount;
    private final String status;

    public AdminReservationRow(Integer reservationId, String roomName, String guestName, String hostName,
                               LocalDateTime startAt, LocalDateTime endAt, Integer amount, String status) {
        this.reservationId = reservationId;
        this.roomName = roomName;
        this.guestName = guestName;
        this.hostName = hostName;
        this.startAt = startAt;
        this.endAt = endAt;
        this.amount = amount;
        this.status = status;
    }

    public Integer getReservationId() { return reservationId; }
    public String getRoomName() { return roomName; }
    public String getGuestName() { return guestName; }
    public String getHostName() { return hostName; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public Integer getAmount() { return amount; }
    public String getStatus() { return status; }
}

