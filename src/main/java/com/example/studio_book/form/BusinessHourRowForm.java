// src/main/java/com/example/studio_book/form/BusinessHourRowForm.java
package com.example.studio_book.form;

import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BusinessHourRowForm {
    /** 1=Mon ... 7=Sun */
    @Min(1) @Max(7)
    private Integer dayIndex;

    /** 休みなら null 許可 */
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    /** true=休み / false=営業 */
    private boolean holiday;
}
