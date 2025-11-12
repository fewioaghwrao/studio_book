package com.example.studio_book.form;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReservationInputForm {

    @NotNull(message = "開始日を選択してください。")
//    @FutureOrPresent(message = "開始日は本日以降を選択してください。")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "開始時刻を入力してください。")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "終了日を選択してください。")
//    @FutureOrPresent(message = "終了日は本日以降を選択してください。")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @NotNull(message = "終了時刻を入力してください。")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;
    
    public LocalDateTime getStartDateTime() {
        if (startDate == null || startTime == null) return null;
        return LocalDateTime.of(startDate, startTime);
      }

      public LocalDateTime getEndDateTime() {
        if (endDate == null || endTime == null) return null;
        return LocalDateTime.of(endDate, endTime);
      }
}

