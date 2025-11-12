// com.example.studio_book.form.PriceRuleForm
package com.example.studio_book.form;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class PriceRuleForm {
    private Integer roomId;
    private List<PriceRuleRowForm> rows = new ArrayList<>();
}
