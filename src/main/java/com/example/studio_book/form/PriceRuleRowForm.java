// com.example.studio_book.form.PriceRuleRowForm
package com.example.studio_book.form;

import java.math.BigDecimal;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PriceRuleRowForm {
    private Integer id;          // 既存行ならID、未入力OK
    private String ruleType;     // "multiplier" / "flat_fee"
    private Integer weekday;     // 0-6 or null

    // "HH:mm" 形式で受ける
    @Pattern(regexp="^$|^([01]\\d|2[0-3]):[0-5]\\d$")
    private String startHour;

    @Pattern(regexp="^$|^([01]\\d|2[0-3]):[0-5]\\d$")
    private String endHour;

    private BigDecimal multiplier; // 例: 1.5
    private Integer flatFee;       // 例: 2000
    private String note;

    // 画面の行削除フラグ
    private boolean _delete;
}

