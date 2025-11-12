// src/main/java/com/example/studio_book/controller/HostStatsController.java
package com.example.studio_book.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.studio_book.entity.Room;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.HostStatsService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/stats")
public class HostStatsController {

    private final RoomRepository roomRepository;
    private final HostStatsService statsService;

    /** 画面 */
    @GetMapping
    public String index(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        var hostId = principal.getUser().getId();
        List<Room> rooms = roomRepository.findAllByHost(hostId);
        model.addAttribute("rooms", rooms);
        return "host/stats/index"; // Thymeleafテンプレート
    }
}
