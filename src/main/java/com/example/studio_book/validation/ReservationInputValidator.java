// src/main/java/com/example/studio_book/validation/ReservationInputValidator.java
package com.example.studio_book.validation;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import com.example.studio_book.form.ReservationInputForm;
import com.example.studio_book.repository.ClosureRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.service.BusinessHourService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReservationInputValidator implements Validator {

  private final ReservationRepository reservationRepository;
  private final ClosureRepository closureRepository;
  private final BusinessHourService businessHourService;

  @Override
  public boolean supports(Class<?> clazz) {
    return ReservationInputForm.class.isAssignableFrom(clazz);
  }

  /**
   * @param target ReservationInputForm
   * @param errors BindingResult
   * @param roomId チェック対象の部屋ID（Controllerから渡すヘルパー経由で設定）
   */
  public void validateWithRoomId(Object target, Errors errors, int roomId) {
    validate(target, errors); // 基本チェック
    if (errors.hasErrors()) return;

    ReservationInputForm f = (ReservationInputForm) target;
    LocalDateTime s = f.getStartDateTime();
    LocalDateTime e = f.getEndDateTime();

    // 予約済みとの干渉
    if (reservationRepository.existsOverlapping(roomId, s, e)) {
      errors.reject("reservation.overlap", "指定の時間帯は既に予約があります。別の時間を選択してください。");
    }

    // 休館日との干渉
    if (closureRepository.existsOverlapping(roomId, s, e)) {
      errors.reject("closure.overlap", "指定の時間帯は休館です。別の時間を選択してください。");
    }

    // 営業時間外
    if (!businessHourService.fitsWithinBusinessHours(roomId, s, e)) {
      errors.reject("businesshour.outside", "営業時間外を含んでいます。営業時間内の時間帯を指定してください。");
    }
  }

  @Override
  public void validate(Object target, Errors errors) {
    ReservationInputForm f = (ReservationInputForm) target;

    // 必須（@NotNullで基本カバー。ここは念のための重ねがけ）
    if (f.getStartDateTime() == null || f.getEndDateTime() == null) return;

    // 開始 < 終了
    if (!f.getStartDateTime().isBefore(f.getEndDateTime())) {
      errors.rejectValue("startDate", "range.invalid", "開始が終了より前になるように指定してください。");
      errors.rejectValue("startTime", "range.invalid", "開始が終了より前になるように指定してください。");
    }
  }
}

