// src/main/java/com/example/studio_book/controller/HostBusinessHourController.java
package com.example.studio_book.controller;

import java.time.LocalTime;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.studio_book.form.BusinessHoursForm;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.BusinessHourService;
import com.example.studio_book.service.RoomService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/rooms/{roomId}/business-hours")
public class HostBusinessHourController {

    private final BusinessHourService businessHourService;
    private final RoomService roomService; // 所有確認用

    @ModelAttribute("weekdayLabels")
    public String[] weekdayLabels() {
        // index: 1..7
        return new String[] { "", "月", "火", "水", "木", "金", "土", "日" };
    }

    /** 表示 */
    @GetMapping
    public String edit(@AuthenticationPrincipal UserDetailsImpl principal,
                       @PathVariable Integer roomId,
                       Model model) {
        // オーナー権限チェック（RoomService側で hostId と一致確認する想定）
        roomService.assertOwnedBy(roomId, principal.getUser().getId());

        var room = roomService.findOwned(roomId, principal.getUser().getId()); // 所有確認つき取得
        BusinessHoursForm form = businessHourService.loadOrDefault(roomId);
        model.addAttribute("form", form);
        model.addAttribute("room", room);   // ★ 追加
        return "host/rooms/business-hours/edit";
    }

    /** 保存 */
    @PostMapping
    @Transactional
    public String update(@AuthenticationPrincipal UserDetailsImpl principal,
                         @PathVariable Integer roomId,
                         @Valid @ModelAttribute("form") BusinessHoursForm form,
                         BindingResult bindingResult,
                         Model model) {
        roomService.assertOwnedBy(roomId, principal.getUser().getId());

        
        // --- ここから追加：クロスフィールド/業務ルール検証 ---
        if (form.getRows() != null) {
            for (int i = 0; i < form.getRows().size(); i++) {
                var row = form.getRows().get(i);

                if (row.isHoliday()) {
                    // 休日は開始・終了をnullで保存する運用なら、ここで強制的にnullにしてもOK
                    // row.setStartTime(null);
                    // row.setEndTime(null);
                    continue;
                }

                LocalTime start = row.getStartTime();
                LocalTime end   = row.getEndTime();

                // 必須チェック
                if (start == null) {
                    bindingResult.rejectValue("rows[" + i + "].startTime", "time.required", "開始時刻を選択してください。");
                }
                if (end == null) {
                    bindingResult.rejectValue("rows[" + i + "].endTime", "time.required", "終了時刻を選択してください。");
                }

                // 15分刻み（将来API等からの入力も想定して堅牢化）
                if (start != null && (start.getSecond() != 0 || start.getNano() != 0 || (start.getMinute() % 15) != 0)) {
                    bindingResult.rejectValue("rows[" + i + "].startTime", "time.step", "15分単位で指定してください。");
                }
                if (end != null && (end.getSecond() != 0 || end.getNano() != 0 || (end.getMinute() % 15) != 0)) {
                    bindingResult.rejectValue("rows[" + i + "].endTime", "time.step", "15分単位で指定してください。");
                }

                // 順序（終了は開始より後）
                if (start != null && end != null && !end.isAfter(start)) {
                    bindingResult.rejectValue("rows[" + i + "].endTime", "time.order", "終了は開始より後の時刻を指定してください。");
                }
            }
        }
        // --- 追加ここまで ---
        
        if (bindingResult.hasErrors()) {
            return "host/rooms/business-hours/edit";
        }
        try {
            businessHourService.save(roomId, form);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "host/rooms/business-hours/edit";
        }
        return "redirect:/host/rooms?success";
    }
    
    @ModelAttribute("room")
    public Object room(@AuthenticationPrincipal UserDetailsImpl principal,
                       @PathVariable Integer roomId) {
        // ここは所有確認後に呼ばれる想定だが、二重チェックしたい場合は assertOwnedBy を入れてもOK
        return roomService.findOwned(roomId, principal.getUser().getId());
    }
}
