// src/main/java/com/example/studio_book/form/AdminSettingsForm.java
package com.example.studio_book.form;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminSettingsForm {

    /** 画面表示・入力用（%）: 例 10, 15, 10.5 */
    @NotNull(message = "税率は必須です")
    @DecimalMin(value = "0.0", inclusive = true, message = "税率は0以上で入力してください")
    @DecimalMax(value = "100.0", inclusive = true, message = "税率は100以下で入力してください")
    @Digits(integer = 3, fraction = 2, message = "小数点以下は2桁まで")
    private BigDecimal taxRatePercent;

    @NotNull(message = "手数料は必須です")
    @DecimalMin(value = "0.0", inclusive = true, message = "手数料は0以上で入力してください")
    @DecimalMax(value = "100.0", inclusive = true, message = "手数料は100以下で入力してください")
    @Digits(integer = 3, fraction = 2, message = "小数点以下は2桁まで")
    private BigDecimal adminFeeRatePercent;
}

