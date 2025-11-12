package com.example.studio_book.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewForm {
    @Min(1) @Max(5)
    private Integer score;

    @NotBlank
    private String content;

    private Integer roomId;        // hidden
    private Integer reservationId; // hidden（任意）
}
