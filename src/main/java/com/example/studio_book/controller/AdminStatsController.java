// src/main/java/com/example/studio_book/controller/AdminStatsController.java
package com.example.studio_book.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.studio_book.dto.AdminStatsApiDto;
import com.example.studio_book.dto.RoomOptionDto;
import com.example.studio_book.service.AdminStatsService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/stats")
public class AdminStatsController {

    private final AdminStatsService service;

    @GetMapping
    public String index(Model model) {
        List<RoomOptionDto> rooms = service.loadRoomOptionsWithHost();
        model.addAttribute("rooms", rooms);
        return "admin/stats/index";
    }

    @GetMapping("/api")
    @ResponseBody
    public AdminStatsApiDto api(@RequestParam(name = "roomId", required = false) Integer roomId) {
        return service.buildDashboard(roomId == null ? 0 : roomId);
    }
}

