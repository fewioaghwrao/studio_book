// src/main/java/com/example/studio_book/controller/AuditLogController.java
package com.example.studio_book.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.studio_book.entity.AuditLog;
import com.example.studio_book.repository.AuditLogRepository;

@Controller
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/admin/logs")
    public String list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            // ★ 検索パラメータ
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "actorId", required = false) Integer actorId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "entity", required = false) String entity,
            @RequestParam(name = "entityId", required = false) Integer entityId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {

        Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "ts"));

        Specification<AuditLog> spec = Specification.where(null);

        // キーワード（action / entity / note の部分一致・大文字小文字無視）
        if (StringUtils.hasText(q)) {
            final String like = "%" + q.toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("action")), like),
                    cb.like(cb.lower(root.get("entity")), like),
                    cb.like(cb.lower(root.get("note")), like)
            ));
        }

        if (actorId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (entityId != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (StringUtils.hasText(action)) {
            final String like = "%" + action.toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.like(cb.lower(root.get("action")), like));
        }
        if (StringUtils.hasText(entity)) {
            final String like = "%" + entity.toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.like(cb.lower(root.get("entity")), like));
        }

        if (from != null) {
            LocalDateTime fromDt = from.atStartOfDay();
            spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("ts"), fromDt));
        }
        if (to != null) {
            LocalDateTime toDt = to.atTime(LocalTime.MAX);
            spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("ts"), toDt));
        }

        Page<AuditLog> logs = auditLogRepository.findAll(spec, pageable);

        // ビューへ現在の検索条件を戻す（入力値保持・ページリンク維持用）
        model.addAttribute("logs", logs);
        model.addAttribute("q", q);
        model.addAttribute("actorId", actorId);
        model.addAttribute("action", action);
        model.addAttribute("entity", entity);
        model.addAttribute("entityId", entityId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);

        return "admin/logs";
    }
}

