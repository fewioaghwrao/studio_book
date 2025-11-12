// src/main/java/com/example/studio_book/controller/HostSalesController.java
package com.example.studio_book.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.studio_book.dto.HostSalesRowProjection;
import com.example.studio_book.repository.ReservationChargeItemRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/sales_details")
public class HostSalesController {

    private final ReservationRepository reservationRepository;
    private final ReservationChargeItemRepository chargeItemRepository;

    @GetMapping
    public String index(@AuthenticationPrincipal UserDetailsImpl principal,
                        @RequestParam(required = false) Integer roomId,
                        @RequestParam(defaultValue = "true") boolean onlyWithItems,
                        @PageableDefault(size = 5, sort = "startAt", direction = Sort.Direction.DESC) Pageable pageable,
                        Model model) {

        var hostId = principal.getUser().getId();

        // スタジオ選択用
        var roomOptions = reservationRepository.findRoomOptionsForHost(hostId);

        // 明細の有無（EXISTS 条件）フラグ
        int only = onlyWithItems ? 1 : 0;

        Page<HostSalesRowProjection> page =
            reservationRepository.findSalesDetailsForHost(hostId, only, roomId, pageable);

        model.addAttribute("rows", page.getContent());
        model.addAttribute("page", page);
        model.addAttribute("roomOptions", roomOptions);
        model.addAttribute("selectedRoomId", roomId);
        model.addAttribute("onlyWithItems", onlyWithItems);

        return "host/sales_details/index";
    }
    
    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal UserDetailsImpl principal,
                         @PathVariable("id") Integer id,
                         org.springframework.ui.Model model) {

        var hostId = principal.getUser().getId();

        var head = reservationRepository.findSalesHeadOne(hostId, id)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND));

        var items = chargeItemRepository.findByReservationIdOrderBySliceStartAsc(id);

        model.addAttribute("reservation", head);
        model.addAttribute("items", items);
        return "host/sales_details/detail";
    }
}
