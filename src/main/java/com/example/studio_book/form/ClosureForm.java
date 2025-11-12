// src/main/java/com/example/studio_book/form/ClosureForm.java
package com.example.studio_book.form;


import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ClosureForm {
    @NotNull(message = "開始日時は必須です")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;

    @NotNull(message = "終了日時は必須です")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;

    @Size(max = 255, message = "理由は255文字以内で入力してください")
    private String reason;

    @AssertTrue(message = "終了日時は開始日時以降を指定してください")
    public boolean isValidRange() {
        if (startAt == null || endAt == null) return true;
        return !endAt.isBefore(startAt);
    }
}

