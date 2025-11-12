// src/main/java/com/example/studio_book/dto/HostReservationRow.java
package com.example.studio_book.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HostReservationRow {
    private Integer reservationId;
    private String roomName;
    private String guestName;
    private LocalDateTime startAt;  // ← startTime → startAt に変更
    private LocalDateTime endAt;    // ← endTime → endAt に変更
    private Integer amount;         // ← price → amount に変更
    private String status;       // "booked" | "paid" | "canceled"
}
