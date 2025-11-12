// src/main/java/com/example/studio_book/viewmodel/PriceRuleViewModel.java
package com.example.studio_book.viewmodel;

import java.math.BigDecimal;

import com.example.studio_book.entity.PriceRule;

import lombok.Getter;

@Getter
public class PriceRuleViewModel {
    private final PriceRule rule;   // 元のルール
    private final BigDecimal amount; // 計算済みの金額（円・整数丸め推奨）

    public PriceRuleViewModel(PriceRule rule, BigDecimal amount) {
        this.rule = rule;
        this.amount = amount;
    }
}

