package com.example.studio_book.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.example.studio_book.entity.Room;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.service.RoomService;

@Controller
public class HomeController {
	
    private final RoomService roomService;
    private final ReviewRepository reviewRepository;

    public HomeController(RoomService roomService,ReviewRepository reviewRepository) {
        this.roomService = roomService;
        this.reviewRepository = reviewRepository;
    }    
	
    @GetMapping("/")
    public String index(Model model) {
        List<Room> newRooms = roomService.findTop8RoomsByOrderByCreatedAtDesc();
        List<Room> popularRooms = roomService.findTop3RoomsByOrderByReservationCountDesc();
        model.addAttribute("newRooms", newRooms);   
        model.addAttribute("popularRooms", popularRooms);
        
        // ★ 集計対象の roomId をひとまとめに
        Set<Integer> ids = new HashSet<>();
        popularRooms.forEach(r -> ids.add(r.getId()));
        newRooms.forEach(r -> ids.add(r.getId()));

     // ★ 公開レビューのみを対象に平均・件数をまとめて取得
        Map<Integer, Double> avgScoreMap = new HashMap<>();
        for (Object[] row : reviewRepository.findAveragePublicScoresByRoomIds(ids)) {
            avgScoreMap.put((Integer) row[0], (Double) row[1]);
        }

        Map<Integer, Long> reviewCountMap = new HashMap<>();
        for (Object[] row : reviewRepository.countPublicByRoomIds(ids)) {
            reviewCountMap.put((Integer) row[0], (Long) row[1]);
        }

        model.addAttribute("avgScoreMap", avgScoreMap);
        model.addAttribute("reviewCountMap", reviewCountMap);
        
        return "index";
    }
}
