// src/main/java/com/example/studio_book/controller/HostReservationController.java
package com.example.studio_book.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/reservations")
public class HostReservationController {

    private final ReservationRepository reservationRepository;

    /** 一覧表示 */
    @GetMapping
    public String index(@AuthenticationPrincipal UserDetailsImpl principal,
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "kw", required = false) String kw,
                        @RequestParam(name = "status", required = false) String status,
                        @RequestParam(name = "reservationId", required = false) Integer reservationId,
                        @RequestParam(name = "roomId", required = false) Integer roomId,
                        @RequestParam(name = "startFrom", required = false)
                          @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                          java.time.LocalDate startFrom,
                        @RequestParam(name = "startTo", required = false)
                          @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                          java.time.LocalDate startTo,
                        Model model) {

        var hostId = principal.getUser().getId();
        var pageable = PageRequest.of(Math.max(page, 0), 5, Sort.by(Sort.Direction.DESC, "startAt"));

        String kwNorm = (kw != null && !kw.isBlank()) ? kw.trim() : null;
        String statusNorm = (status != null && !status.isBlank()) ? status : null;
        java.time.LocalDateTime fromDt = (startFrom != null) ? startFrom.atStartOfDay() : null;
        java.time.LocalDateTime toDt   = (startTo   != null) ? startTo.atTime(23,59,59, 999_000_000) : null;

        Page<com.example.studio_book.dto.HostReservationRow> p =
            reservationRepository.findPageForHostFiltered(
                hostId, kwNorm, statusNorm, fromDt, toDt, reservationId, roomId, pageable
            );

        model.addAttribute("page", p);
        model.addAttribute("rows", p.getContent());

        // 検索条件の戻し
        model.addAttribute("kw", kwNorm);
        model.addAttribute("status", statusNorm);
        model.addAttribute("reservationId", reservationId);
        model.addAttribute("roomId", roomId);
        model.addAttribute("startFrom", (startFrom != null) ? startFrom.toString() : null);
        model.addAttribute("startTo",   (startTo   != null) ? startTo.toString()   : null);

        // (任意) スタジオ選択肢
        model.addAttribute("roomOptions", reservationRepository.findRoomOptionsForHost(hostId));

        return "host/reservations/index";
    }

    /** 承認（→ status: paid） */
    @PostMapping("/{id}/approve")
    @Transactional
    public String approve(@PathVariable Integer id,
                          @AuthenticationPrincipal UserDetailsImpl principal) {

        var hostId = principal.getUser().getId();
        var r = reservationRepository.findByIdAndRoom_User_Id(id, hostId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // booked → paid のみ許可（既にcanceled/paidなら無視）
        if ("booked".equalsIgnoreCase(r.getStatus())) {
            r.setStatus("paid");
            // 必要なら監査カラム等：r.setCheckedInAt(LocalDateTime.now());
            reservationRepository.save(r);
        }
        return "redirect:/host/reservations?approved=1";
    }

    /** キャンセル（→ status: canceled） */
    @PostMapping("/{id}/cancel")
    @Transactional
    public String cancel(@PathVariable Integer id,
                         @AuthenticationPrincipal UserDetailsImpl principal) {

        var hostId = principal.getUser().getId();
        var r = reservationRepository.findByIdAndRoom_User_Id(id, hostId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // booked → canceled のみ許可（paidは運用上キャンセル不可にしている想定）
        if ("booked".equalsIgnoreCase(r.getStatus())) {
            r.setStatus("canceled");
            // 必要なら監査：r.setCanceledAt(LocalDateTime.now()); r.setCancelReason("host");
            reservationRepository.save(r);
        }
        return "redirect:/host/reservations?canceled=1";
    }
}

