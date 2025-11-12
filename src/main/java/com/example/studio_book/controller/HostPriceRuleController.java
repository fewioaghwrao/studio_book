// com.example.studio_book.controller.HostPriceRuleController
package com.example.studio_book.controller;

import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.studio_book.entity.PriceRule;
import com.example.studio_book.form.PriceRuleForm;
import com.example.studio_book.form.PriceRuleRowForm;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.RoomRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/rooms")
public class HostPriceRuleController {

    private final PriceRuleRepository priceRuleRepository;
    private final RoomRepository roomRepository;

    @GetMapping("/{roomId}/price-rules")
    public String edit(@PathVariable Integer roomId, Model model) {
        var room = roomRepository.findById(roomId).orElseThrow();

        var rules = priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(roomId);
        var form = buildFormFrom(rules, roomId); // rows[0]を1件分だけ初期化

        model.addAttribute("room", room);
        model.addAttribute("form", form);
        model.addAttribute("rules", rules);
        return "host/price_rules/edit";
    }

    /** 追加専用 */
    @PostMapping("/{roomId}/price-rules")
    @Transactional
    public String add(@PathVariable Integer roomId,
                      @ModelAttribute("form") @Valid PriceRuleForm form,
                      BindingResult binding,
                      Model model) {

        var room = roomRepository.findById(roomId).orElseThrow();

        // 1件追加方式: rows 先頭のみを見る
        PriceRuleRowForm row = form.getRows().isEmpty() ? new PriceRuleRowForm() : form.getRows().get(0);

        // サーバ側バリデーション（相互排他 & 15分刻み & 必須）
        validateRow(roomId, row, binding);

        if (binding.hasErrors()) {
            var current = priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(roomId);
            model.addAttribute("room", room);
            model.addAttribute("rules", current);
            return "host/price_rules/edit";
        }

        var entity = new PriceRule();
        entity.setRoomId(roomId);
        entity.setRuleType(row.getRuleType());
        entity.setWeekday(row.getWeekday());
        entity.setStartHour(safeParse(row.getStartHour()));
        entity.setEndHour(safeParse(row.getEndHour()));
        entity.setMultiplier(row.getMultiplier());
        entity.setFlatFee(row.getFlatFee());
        entity.setNote(row.getNote());
        priceRuleRepository.save(entity);

        // 再描画
        List<PriceRule> latest = priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(roomId);
        PriceRuleForm freshForm = buildFormFrom(latest, roomId);

        model.addAttribute("room", room);
        model.addAttribute("form", freshForm);
        model.addAttribute("rules", latest);
        model.addAttribute("saved", true);
        return "host/price_rules/edit";
    }

    /** 個別削除 */
    @PostMapping("/{roomId}/price-rules/{ruleId}/delete")
    @Transactional
    public String delete(@PathVariable Integer roomId,
                         @PathVariable Integer ruleId,
                         Model model) {
        var room = roomRepository.findById(roomId).orElseThrow();

        // 念のためroomId一致を確認してから削除
        priceRuleRepository.findById(ruleId).ifPresent(r -> {
            if (Objects.equals(r.getRoomId(), roomId)) {
                priceRuleRepository.deleteById(ruleId);
            }
        });

        var latest = priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(roomId);
        var freshForm = buildFormFrom(latest, roomId);

        model.addAttribute("room", room);
        model.addAttribute("form", freshForm);
        model.addAttribute("rules", latest);
        model.addAttribute("deleted", true);
        return "host/price_rules/edit";
    }

    private LocalTime safeParse(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        return LocalTime.parse(hhmm); // "HH:mm"
    }

    /** rows[0] を1行分だけ持つフォームを作る（初期表示用） */
    private PriceRuleForm buildFormFrom(List<PriceRule> rules, Integer roomId) {
        var form = new PriceRuleForm();
        form.setRoomId(roomId);

        // 空行1つだけ（追加用）
        var row = new PriceRuleRowForm();
        form.getRows().add(row);

        return form;
    }

    /** 相互排他・15分刻みなどのサーバ側チェック */
    private void validateRow(Integer roomId, PriceRuleRowForm row, BindingResult binding) {
        String type = row.getRuleType();

        if (type == null || type.isBlank()) {
            binding.rejectValue("rows[0].ruleType", "ruleType.required", "タイプを選択してください。");
            return;
        }

        if ("flat_fee".equals(type)) {
            // 固定費：固定費必須、時刻・倍率は入力不可
            if (row.getFlatFee() == null) {
                binding.rejectValue("rows[0].flatFee", "flatFee.required", "固定費を入力してください。");
            }
            if (notEmpty(row.getStartHour()) || notEmpty(row.getEndHour()) || row.getMultiplier() != null) {
                binding.reject("flatFee.only", "固定費の場合、開始/終了時刻と倍率は入力できません。");
            }
            
            // ★ 追加：同一曜日の固定費は1件まで（weekday は null=全て も含む）
            Integer weekday = row.getWeekday(); // null=全て
            boolean dup = priceRuleRepository.existsByRoomIdAndRuleTypeAndWeekday(roomId, "flat_fee", weekday);
            if (dup) {
                // weekday のフォームフィールドに紐づけてエラー表示
                binding.rejectValue("rows[0].weekday", "flatFee.duplicate",
                    "同一曜日の固定費はすでに登録されています。");
            }
            
        } else if ("multiplier".equals(type)) {
            // 倍率：倍率・開始/終了時刻必須、固定費は入力不可
            if (row.getMultiplier() == null) {
                binding.rejectValue("rows[0].multiplier", "multiplier.required", "倍率を入力してください。");
            }
            if (isEmpty(row.getStartHour()) || isEmpty(row.getEndHour())) {
                binding.reject("time.required", "開始時刻・終了時刻を選択してください。");
            } else {
                // 15分刻みチェック
                LocalTime s = safeParse(row.getStartHour());
                LocalTime e = safeParse(row.getEndHour());
                if (!isQuarter(s) || !isQuarter(e)) {
                    binding.reject("time.quarter", "開始・終了は15分刻みで指定してください。");
                }
            }
            if (row.getFlatFee() != null) {
                binding.rejectValue("rows[0].flatFee", "flatFee.forbidden", "倍率の場合、固定費は入力できません。");
            }
        } else {
            binding.rejectValue("rows[0].ruleType", "ruleType.invalid", "不正なタイプです。");
        }
    }

    private boolean isQuarter(LocalTime t) {
        if (t == null) return false;
        return t.getMinute() % 15 == 0 && t.getSecond() == 0 && t.getNano() == 0;
    }
    private boolean notEmpty(String s) { return s != null && !s.isBlank(); }
    private boolean isEmpty(String s) { return s == null || s.isBlank(); }
}


