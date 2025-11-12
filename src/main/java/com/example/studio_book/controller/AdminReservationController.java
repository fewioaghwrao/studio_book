package com.example.studio_book.controller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import com.example.studio_book.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/reservations")
public class AdminReservationController {

    private final ReservationRepository reservationRepository;

    /** 一覧表示 */
    @GetMapping
    public String index(
        @org.springframework.web.bind.annotation.RequestParam(name = "page", defaultValue = "0") int page,
        @org.springframework.web.bind.annotation.RequestParam(name = "kw", required = false) String kw,
        @org.springframework.web.bind.annotation.RequestParam(name = "status", required = false) String status,
        @org.springframework.web.bind.annotation.RequestParam(name = "startFrom", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate startFrom,
        @org.springframework.web.bind.annotation.RequestParam(name = "startTo", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate startTo,
        @org.springframework.web.bind.annotation.RequestParam(name = "reservationId", required = false) Integer reservationId,
        Model model
    ) {
        var pageable = org.springframework.data.domain.PageRequest.of(Math.max(page, 0), 5);

        // 文字列ブランク → null
        String kwNorm = (kw != null && !kw.isBlank()) ? kw.trim() : null;
        String statusNorm = (status != null && !status.isBlank()) ? status : null;

        // 日付 → LocalDateTime へ（開始は 00:00、終端は 23:59:59.999）
        java.time.LocalDateTime fromDt = (startFrom != null) ? startFrom.atStartOfDay() : null;
        java.time.LocalDateTime toDt = (startTo != null) ? startTo.atTime(23, 59, 59, 999_000_000) : null;

        var pageData = reservationRepository.findAdminReservationPageFiltered(
            kwNorm, statusNorm, fromDt, toDt, reservationId, pageable
        );

        model.addAttribute("page", pageData);
        model.addAttribute("rows", pageData.getContent());

        // 画面に検索条件を戻す
        model.addAttribute("kw", kwNorm);
        model.addAttribute("status", statusNorm);
        model.addAttribute("startFrom", (startFrom != null) ? startFrom.toString() : null);
        model.addAttribute("startTo", (startTo != null) ? startTo.toString() : null);
        model.addAttribute("reservationId", reservationId);

        return "admin/reservations/index";
    }
    @PostMapping("/{id}/approve")
    @org.springframework.transaction.annotation.Transactional
    public String approve(@PathVariable Integer id) {

        var r = reservationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // ★ 強制：承認 → paid
        r.setStatus("paid");
        reservationRepository.save(r);

        return "redirect:/admin/reservations?approved=1";
    }

    @PostMapping("/{id}/cancel")
    @org.springframework.transaction.annotation.Transactional
    public String cancel(@PathVariable Integer id) {

        var r = reservationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // ★ 強制：キャンセル → canceled
        r.setStatus("canceled");
        reservationRepository.save(r);

        return "redirect:/admin/reservations?canceled=1";
    }

    @PostMapping("/{id}/clear")
    @org.springframework.transaction.annotation.Transactional
    public String clear(@PathVariable Integer id) {

        var r = reservationRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // ★ 強制：クリア → booked
        r.setStatus("booked");
        reservationRepository.save(r);

        return "redirect:/admin/reservations?cleared=1";
    }
}
