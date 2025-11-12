// HostRoomController.java
package com.example.studio_book.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.example.studio_book.entity.PriceRule;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.RoomBusinessHourRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.viewmodel.PriceRuleViewModel;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host/rooms")
public class HostRoomController {

    private final RoomRepository roomRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final RoomBusinessHourRepository roomBusinessHourRepository; // ★追加

    // 一覧（本人のスタジオのみ）
    @GetMapping
    public String index(@AuthenticationPrincipal UserDetailsImpl principal,
                        @RequestParam(name = "keyword", required = false) String keyword,
                        @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
                        Model model) {

        User me = principal.getUser();
        Page<Room> page = (keyword != null && !keyword.isBlank())
                ? roomRepository.findByUser_IdAndNameContainingIgnoreCase(me.getId(), keyword.trim(), pageable)
                : roomRepository.findByUser_Id(me.getId(), pageable);

        model.addAttribute("roomPage", page);
        model.addAttribute("keyword", keyword);
        return "host/rooms/index"; // ← 下のThymeleafに対応
    }

    /** 詳細（本人所有チェック＋金額計算込み） */
    @GetMapping("/{id}")
    public String show(@AuthenticationPrincipal UserDetailsImpl principal,
                       @PathVariable Integer id,
                       Model model) {

        // 本人所有ルームのみ
        Room room = roomRepository.findByIdAndUser_Id(id, principal.getUser().getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // 念のための所有者チェック
        if (room.getUser() == null || room.getUser().getId() == null
            || !room.getUser().getId().equals(principal.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // 全ルール取得
        List<PriceRule> rules = priceRuleRepository.findByRoomId(id);

        // 並び順：全日(NULL)→曜日昇順(0-6)→開始→終了（NULLは最小）
        Comparator<PriceRule> order =
            Comparator.comparing(PriceRule::getWeekday, HostRoomController::nullFirstCompare)
                      .thenComparing(PriceRule::getStartHour, HostRoomController::nullFirstCompare)
                      .thenComparing(PriceRule::getEndHour, HostRoomController::nullFirstCompare);

        // 固定費（そのまま渡す）
        List<PriceRule> flatFeeRules = rules.stream()
            .filter(r -> "flat_fee".equals(r.getRuleType()) && r.getFlatFee() != null)
            .sorted(order)
            .toList();

        // 加算料金：ここで (倍率×基本料金) を BigDecimal で計算して ViewModel に詰める
        BigDecimal base = BigDecimal.valueOf(room.getPrice()); // 基本料金（円）
        List<PriceRuleViewModel> multiplierRules = rules.stream()
            .filter(r -> "multiplier".equals(r.getRuleType()) && r.getMultiplier() != null)
            .sorted(order)
            .map(r -> {
                BigDecimal amount = base.multiply(r.getMultiplier())
                                        .setScale(0, RoundingMode.HALF_UP); // 円丸め
                return new PriceRuleViewModel(r, amount);
            })
            .toList();

        model.addAttribute("room", room);
        model.addAttribute("flatFeeRules", flatFeeRules);
        model.addAttribute("multiplierRules", multiplierRules);
        
        // 営業時間を day_index 昇順で取得（1..7）
        var hours = roomBusinessHourRepository.findByRoomIdOrderByDayIndexAsc(id);
        model.addAttribute("businessHours", hours);

        return "host/rooms/show";
    }

    // 削除（本人所有チェック）
    @PostMapping("/{id}/delete")
    public String delete(@AuthenticationPrincipal UserDetailsImpl principal,
                         @PathVariable Integer id,
                         Model model) {

     // 所有者チェック
        Room room = roomRepository.findByIdAndUser_Id(id, principal.getUser().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        roomRepository.delete(room);
        
        // 成功メッセージはリダイレクト先でフラッシュスコープ等を使ってもOK
        return "redirect:/host/rooms?success";
    }
    
 // null を先にする比較（Comparable 同士 or null）
    private static <T extends Comparable<T>> int nullFirstCompare(T a, T b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }
    
}

