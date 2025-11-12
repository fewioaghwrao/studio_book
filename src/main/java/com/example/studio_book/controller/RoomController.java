package com.example.studio_book.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.studio_book.entity.PriceRule;
import com.example.studio_book.entity.Review;
import com.example.studio_book.entity.Room;
import com.example.studio_book.form.ReservationInputForm;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.ReviewRepository;   // ★ 追加
import com.example.studio_book.repository.RoomBusinessHourRepository;
import com.example.studio_book.service.RoomService;
import com.example.studio_book.viewmodel.PriceRuleViewModel;

@Controller
@RequestMapping("/rooms")
public class RoomController {
    private final RoomService roomService;
    private final ReviewRepository reviewRepository;          // ★ 追加
    private final RoomBusinessHourRepository roomBusinessHourRepository;          // ★ 追加
    private final PriceRuleRepository priceRuleRepository;  
    
    public RoomController(RoomService roomService,
            ReviewRepository reviewRepository,
            RoomBusinessHourRepository roomBusinessHourRepository,
            PriceRuleRepository priceRuleRepository) {          // ★ 追加
this.roomService = roomService;
this.reviewRepository = reviewRepository;
this.roomBusinessHourRepository = roomBusinessHourRepository;
this.priceRuleRepository = priceRuleRepository;       // ★ 追加
}

    @GetMapping
    public String index(@RequestParam(name = "keyword", required = false) String keyword,
                        @RequestParam(name = "area", required = false) String area,
                        @RequestParam(name = "price", required = false) Integer price,
                        @RequestParam(name = "order", required = false) String order,
                        @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
                        Model model)
    {
        Page<Room> roomPage;

        if (keyword != null && !keyword.isEmpty()) {
            roomPage = "priceAsc".equals(order)
                    ? roomService.findRoomsByNameLikeOrAddressLikeOrderByPriceAsc(keyword, keyword, pageable)
                    : roomService.findRoomsByNameLikeOrAddressLikeOrderByCreatedAtDesc(keyword, keyword, pageable);
        } else if (area != null && !area.isEmpty()) {
            roomPage = "priceAsc".equals(order)
                    ? roomService.findRoomsByAddressLikeOrderByPriceAsc(area, pageable)
                    : roomService.findRoomsByAddressLikeOrderByCreatedAtDesc(area, pageable);
        } else if (price != null) {
            roomPage = "priceAsc".equals(order)
                    ? roomService.findRoomsByPriceLessThanEqualOrderByPriceAsc(price, pageable)
                    : roomService.findRoomsByPriceLessThanEqualOrderByCreatedAtDesc(price, pageable);
        } else {
            roomPage = "priceAsc".equals(order)
                    ? roomService.findAllRoomsByOrderByPriceAsc(pageable)
                    : roomService.findAllRoomsByOrderByCreatedAtDesc(pageable);
        }

        model.addAttribute("roomPage", roomPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("area", area);
        model.addAttribute("price", price);
        model.addAttribute("order", order);
        
        // ★ 一覧に表示する平均スコアは「公開のみ」で計算（ページに表示されているroomだけ対象）
        var roomIds = roomPage.map(Room::getId).getContent();
        Map<Integer, Double> avgScoreMap = new HashMap<>();
        if (!roomIds.isEmpty()) {
            List<Object[]> rows = reviewRepository.findAveragePublicScoreByRoomIds(roomIds); // ←Repositoryに追加が必要
            for (Object[] row : rows) {
                Integer roomId = (Integer) row[0];
                Double avg = (Double) row[1];
                avgScoreMap.put(roomId, avg);
            }
        }
        model.addAttribute("avgScoreMap", avgScoreMap);

        return "rooms/index";
    }
    
    @GetMapping("/{id}")
    public String show(@PathVariable(name = "id") Integer id, 
    		  @RequestParam(defaultValue = "0") int page,   
    		RedirectAttributes redirectAttributes, 
    		Model model) {
        Optional<Room> optionalRoom  = roomService.findRoomById(id);

        if (optionalRoom.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "スタジオが存在しません。");

            return "redirect:/rooms";
        }

        Room room = optionalRoom.get();
        model.addAttribute("room", room);
        model.addAttribute("reservationInputForm", new ReservationInputForm());
        
        // ★ 公開レビューのみ（本文・星を出す対象）: 5件/ページを推奨
        var pageable = PageRequest.of(page, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviewsPage =
                reviewRepository.findByRoomIdAndPublicVisibleTrueOrderByCreatedAtDesc(id, pageable);
        model.addAttribute("reviewsPage", reviewsPage);

        // ★ 非公開レビューだが「ホスト返信あり」は別枠表示（本文/星は出さず、ホスト返信のみ）
        var hiddenWithReply =
                reviewRepository.findByRoomIdAndPublicVisibleFalseAndHostReplyIsNotNullOrderByHostReplyAtDesc(id);
        model.addAttribute("hiddenWithReply", hiddenWithReply);

        // ★ 平均＆件数は公開のみ
        Double avgScore = reviewRepository.findAveragePublicScoreByRoomId(id);
        long reviewCount = reviewRepository.countByRoomIdAndPublicVisibleTrue(id);
        model.addAttribute("avgScore", avgScore == null ? 0.0 : avgScore);
        model.addAttribute("reviewCount", reviewCount);
        
        // 営業時間を day_index 昇順で取得（1..7）
        var hours = roomBusinessHourRepository.findByRoomIdOrderByDayIndexAsc(id);
        model.addAttribute("businessHours", hours);
        
     // すべてのルールを取得
        List<PriceRule> rules = priceRuleRepository.findByRoomId(id);

        // 並び順：全日(null) → 曜日昇順(0..6) → startHour → endHour （nullは先）
        Comparator<PriceRule> order =
            Comparator.comparing(PriceRule::getWeekday, RoomController::nullFirstCompare)
                      .thenComparing(PriceRule::getStartHour, RoomController::nullFirstCompare)
                      .thenComparing(PriceRule::getEndHour,   RoomController::nullFirstCompare);

        // 固定費（そのまま表示）
        List<PriceRule> flatFeeRules = rules.stream()
            .filter(r -> "flat_fee".equals(r.getRuleType()) && r.getFlatFee() != null)
            .sorted(order)
            .toList();

        // 加算料金：基本料金 × 倍率 を円に丸めて ViewModel へ
        BigDecimal base = BigDecimal.valueOf(room.getPrice()); // 基本料金（円）
        List<PriceRuleViewModel> multiplierRules = rules.stream()
            .filter(r -> "multiplier".equals(r.getRuleType()) && r.getMultiplier() != null)
            .sorted(order)
            .map(r -> {
                BigDecimal amount = base.multiply(r.getMultiplier())
                                        .setScale(0, RoundingMode.HALF_UP);
                return new PriceRuleViewModel(r, amount);
            })
            .toList();

        model.addAttribute("flatFeeRules", flatFeeRules);
        model.addAttribute("multiplierRules", multiplierRules);
        


        return "rooms/show";
    }    
    
    private static <T extends Comparable<T>> int nullFirstCompare(T a, T b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }
}
