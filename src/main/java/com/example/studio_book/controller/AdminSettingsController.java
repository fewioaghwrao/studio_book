// src/main/java/com/example/studio_book/controller/AdminSettingsController.java
package com.example.studio_book.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.studio_book.form.AdminSettingsForm;
import com.example.studio_book.service.AdminSettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;

    @GetMapping
    public String index(Model model) {
        // DBは小数（例 0.10, 0.15）なので×100してフォームにセット
        BigDecimal tax = new BigDecimal(adminSettingsService.getValue("tax_rate", "0"));
        BigDecimal fee = new BigDecimal(adminSettingsService.getValue("admin_fee_rate", "0"));

        AdminSettingsForm form = new AdminSettingsForm();
        form.setTaxRatePercent(tax.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        form.setAdminFeeRatePercent(fee.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));

        model.addAttribute("form", form);
        return "admin/settings/index";
    }

    @PostMapping
    public String update(@Valid @ModelAttribute("form") AdminSettingsForm form,
                         BindingResult bindingResult,
                         RedirectAttributes ra) {

        if (bindingResult.hasErrors()) {
            // バリデーションNG → そのまま戻す
            return "admin/settings/index";
        }

        // % → 小数 例: 10 → 0.10
        BigDecimal taxDecimal = form.getTaxRatePercent()
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP); // 余裕をもって6桁
        BigDecimal feeDecimal = form.getAdminFeeRatePercent()
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

        adminSettingsService.updateValue("tax_rate", taxDecimal.stripTrailingZeros().toPlainString());
        adminSettingsService.updateValue("admin_fee_rate", feeDecimal.stripTrailingZeros().toPlainString());

        ra.addFlashAttribute("successMessage", "保存しました");
        return "redirect:/admin/settings";
    }
}
