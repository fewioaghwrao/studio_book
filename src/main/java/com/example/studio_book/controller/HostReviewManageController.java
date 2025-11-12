// src/main/java/com/example/studio_book/controller/HostReviewManageController.java
package com.example.studio_book.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.studio_book.entity.Review;
import com.example.studio_book.repository.AuditLogRepository;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/reviews")
public class HostReviewManageController {

    private final ReviewRepository reviewRepository;
    private final AuditLogRepository auditLogRepository;
    private final RoomRepository roomRepository; 

    /** レビュー一覧（ホスト横断）＋簡易フィルタ */
    @GetMapping
    @Transactional(readOnly = true)
    public String index(@AuthenticationPrincipal UserDetailsImpl principal,
                        @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                        @RequestParam(required = false) Integer roomId,
                        @RequestParam(required = false) Integer stars,
                        @RequestParam(required = false) Boolean onlyHidden,
                        Model model) {

        Integer hostId = toIntId(principal.getUser().getId());
        
        // ★ ホスト本人のスタジオ一覧を取得してビューへ渡す
        var roomsOwnedByHost = roomRepository.findByUser_IdOrderByNameAsc(hostId);
        model.addAttribute("rooms", roomsOwnedByHost);

        Specification<Review> spec = (root, query, cb) -> {
            // Room → User（= ホスト）へ。join("host") ではなく join("user")
            // ManyToOne なので join を使わず Path で辿ってもOK
            var roomPath  = root.get("room");
            var ownerPath = roomPath.get("user").get("id"); // ← ここが重要

            var p = cb.equal(ownerPath, hostId);

            if (roomId != null) {
                p = cb.and(p, cb.equal(roomPath.get("id"), roomId));
            }
            if (stars != null) {
                p = cb.and(p, cb.equal(root.get("score"), stars));
            }
            if (Boolean.TRUE.equals(onlyHidden)) {
                // プロパティ名は publicVisible（※ isPublic ではない）
                p = cb.and(p, cb.isFalse(root.get("publicVisible")));
            }
            return p;
        };

        Page<Review> page = reviewRepository.findAll(spec, pageable);

        model.addAttribute("page", page);
        model.addAttribute("roomId", roomId);
        model.addAttribute("stars", stars);
        model.addAttribute("onlyHidden", onlyHidden);
        return "host/reviews/index";
    }

    /** ホスト返信の作成/更新（公開リプライ） */
    @PostMapping("/{id}/reply")
    @Transactional
    public String reply(@PathVariable Integer id,
                        @AuthenticationPrincipal UserDetailsImpl principal,
                        @RequestParam String hostReply,
                        RedirectAttributes ra) {

        Review r = reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertOwnedByHostOr403(r, toIntId(principal.getUser().getId()));

        r.setHostReply(hostReply);
        r.setHostReplyAt(LocalDateTime.now());
        reviewRepository.save(r);

        auditLogRepository.add("host_reply", toIntId(principal.getUser().getId()), "review", r.getId());
        ra.addFlashAttribute("message", "返信を保存しました。");
        return "redirect:/host/reviews";
    }

    /** 公開/非公開の切替 */
    @PostMapping("/{id}/visibility")
    @Transactional
    public String toggle(@PathVariable Integer id,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         @RequestParam boolean isPublic,
                         @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {

        Review r = reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertOwnedByHostOr403(r, toIntId(principal.getUser().getId()));

        // エンティティ側は Boolean publicVisible を想定
        r.setPublicVisible(isPublic);
        r.setHiddenReason(isPublic ? null : (
        	    (reason == null || reason.strip().isEmpty()) ? null : reason.strip()
        	));
        reviewRepository.save(r);

        auditLogRepository.add(isPublic ? "review_public_on" : "review_public_off",
                toIntId(principal.getUser().getId()), "review", r.getId());

        ra.addFlashAttribute("message", isPublic ? "公開に変更しました。" : "非公開に変更しました。");
        return "redirect:/host/reviews";
    }

    // ---------------- helper ----------------

    /** 所有権チェック（null安全・型差吸収） */
    private void assertOwnedByHostOr403(Review r, Integer hostId) {
        if (r == null || r.getRoom() == null || r.getRoom().getUser() == null || r.getRoom().getUser().getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Integer ownerId = toIntId(r.getRoom().getUser().getId());
        if (!ownerId.equals(hostId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    /** Long/Integer/String どれでも Integer へ寄せる */
    private Integer toIntId(Object id) {
        if (id == null) return null;
        if (id instanceof Integer i) return i;
        if (id instanceof Long l) return Math.toIntExact(l);
        if (id instanceof String s) return Integer.valueOf(s);
        throw new IllegalArgumentException("Unsupported id type: " + id.getClass());
    }
}



