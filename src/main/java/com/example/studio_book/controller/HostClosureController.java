// src/main/java/com/example/studio_book/controller/HostClosureController.java
package com.example.studio_book.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.studio_book.entity.Closure;
import com.example.studio_book.entity.Room;
import com.example.studio_book.form.ClosureForm;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.ClosureService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/rooms/{roomId}/closures")
public class HostClosureController {

    private final ClosureService closureService;

    /** 画面表示 */
    @GetMapping
    public String index(@PathVariable Integer roomId,
                        @AuthenticationPrincipal UserDetailsImpl principal,
                        Model model) {
        Room room = closureService.getOwnedRoomOrThrow(roomId, principal);
        model.addAttribute("room", room);
        model.addAttribute("closures", closureService.list(roomId, principal));
        model.addAttribute("closureForm", new ClosureForm());
        return "host/closures/index";
    }

    /** イベントJSON（FullCalendar用） */
    @GetMapping(value = "/events", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, Object>> events(@PathVariable Integer roomId,
                                            @AuthenticationPrincipal UserDetailsImpl principal) {
        List<Closure> list = closureService.list(roomId, principal);
        return list.stream().map(c -> {
            final boolean allDay =
                c.getStartAt().toLocalTime().equals(java.time.LocalTime.MIDNIGHT) &&
                c.getEndAt().toLocalTime().equals(java.time.LocalTime.MIDNIGHT);
            String title = (c.getReason() == null || c.getReason().isBlank())
                    ? "休館" : "休館: " + c.getReason();

            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("title", title);
            // ISO-8601（ローカル時刻）でOK。Z付きにしたいなら OffsetDateTime に変換して出力
            m.put("start", c.getStartAt().toString());
            m.put("end",   c.getEndAt().toString());
            m.put("allDay", allDay);
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    /** 追加（終日） */
    @PostMapping
    public String create(@PathVariable Integer roomId,
                         @Valid @ModelAttribute("closureForm") ClosureForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model) {
        if (bindingResult.hasErrors()) {
            // 再表示
            return index(roomId, principal, model);
        }
        try {
        	closureService.create(roomId, form.getStartAt(), form.getEndAt(), form.getReason(), principal);
            return "redirect:/host/rooms/%d/closures?success=1".formatted(roomId);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            return index(roomId, principal, model);
        }
    }

    /** 削除 */
    @PostMapping("/{closureId}/delete")
    public String delete(@PathVariable Integer roomId,
                         @PathVariable Integer closureId,
                         @AuthenticationPrincipal UserDetailsImpl principal) {
        closureService.delete(roomId, closureId, principal);
        return "redirect:/host/rooms/%d/closures?success=1".formatted(roomId);
    }
}

