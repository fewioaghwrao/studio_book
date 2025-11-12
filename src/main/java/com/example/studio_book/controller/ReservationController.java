package com.example.studio_book.controller;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.studio_book.dto.ReservationConfirmDto;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.entity.User;
import com.example.studio_book.form.ReservationInputForm;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomBusinessHourRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.ReservationService;
import com.example.studio_book.service.StripeService;
import com.example.studio_book.validation.ReservationInputValidator;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionRetrieveParams;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@Controller
public class ReservationController {
    private final ReservationService reservationService;
    private final StripeService stripeService; 
    private final RoomRepository roomRepository;
    private final ReviewRepository reviewRepository;
    private final RoomBusinessHourRepository roomBusinessHourRepository;
    private final PriceRuleRepository priceRuleRepository;
    private final ReservationInputValidator reservationInputValidator;

    public ReservationController(ReservationService reservationService,
            StripeService stripeService,
            RoomRepository roomRepository,
            ReviewRepository reviewRepository,
            RoomBusinessHourRepository roomBusinessHourRepository,
            PriceRuleRepository priceRuleRepository,
            ReservationInputValidator reservationInputValidator) {
this.reservationService = reservationService;
this.stripeService = stripeService;

this.roomRepository = roomRepository;
this.reviewRepository = reviewRepository;
this.roomBusinessHourRepository = roomBusinessHourRepository;
this.priceRuleRepository = priceRuleRepository;
this.reservationInputValidator = reservationInputValidator;
}
    
    @Value("${stripe.publishable-key}")
    private String stripePublishableKey;

    @GetMapping("/reservations")
    public String index(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                        @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
                        @RequestParam(value = "reserved", required = false) String reserved,
                        @RequestParam(value = "session_id", required = false) String sessionId,
                        Model model)
    {
        User user = userDetailsImpl.getUser();
        Page<Reservation> reservationPage = reservationService.findReservationsByUserOrderByCreatedAtDesc(user, pageable);

        model.addAttribute("reservationPage", reservationPage);

        
        if (reserved != null) {
            boolean done = false;
            if (sessionId != null) {
                try {
                    SessionRetrieveParams params = SessionRetrieveParams.builder()
                            .addExpand("payment_intent").build();
                    Session sess = Session.retrieve(sessionId, params, null);
                    String piId = sess.getPaymentIntent(); // PaymentIntent ID
                    // Webhookで登録済みか確認（ReservationRepository.existsByPaymentIntentId を利用）
                    done = reservationService.existsByPaymentIntentId(piId);
                } catch (Exception ignore) { /* ネットワーク一時障害などは無視して続行 */ }
            }
            if (done) {
                model.addAttribute("reserved", true);          // 「予約が完了しました。」
            } else {
                model.addAttribute("processing", true);        // 「決済は完了。予約反映中…」等の案内に使う
            }
        }
        
        return "reservations/index";
    }
    @GetMapping("/reservations/confirm")
    public String confirm(@AuthenticationPrincipal UserDetailsImpl userDetailsImpl,
                          RedirectAttributes redirectAttributes,
                          HttpSession httpSession,
                          Model model) {

    	
        System.out.println("[CONFIRM] START");
    	
        // 1) セッションからDTO取得
        ReservationConfirmDto reservationDTO =
            (ReservationConfirmDto) httpSession.getAttribute("reservationDTO");
        
        System.out.println("[CONFIRM] reservationDTO=" + reservationDTO); // ★追加
        
        if (reservationDTO == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "セッションがタイムアウトしました。もう一度予約内容を入力してください。");
            return "redirect:/rooms";
        }

        
        System.out.println("[CONFIRM] createStripeSession...");
        
        // 2) Stripeセッション作成
        User user = userDetailsImpl.getUser();
        System.out.println("[CONFIRM] start createStripeSession roomId=" + reservationDTO.getRoomId()
            + " amount=" + reservationDTO.getAmount());
        String sessionId = stripeService.createStripeSession(reservationDTO, user);
        System.out.println("[CONFIRM] created sessionId=" + sessionId); // ★追加

        // 3) 空ならビューを返さない（ここが重要）
        if (sessionId == null || sessionId.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "決済セッションの作成に失敗しました。時間をおいて再度お試しください。");
            return "redirect:/rooms/" + reservationDTO.getRoomId();
        }

        // 4) ビューに必須データを渡す（th:object と合わせる）
        model.addAttribute("confirm", reservationDTO);       // ★テンプレ名と一致
        model.addAttribute("sessionId", sessionId);          // ★これが data-session-id に入る
        model.addAttribute("stripePublishableKey", stripePublishableKey);
        System.out.println("[CONFIRM] OK → reservations/confirm");
        
        return "reservations/confirm";
    }
    
    @PostMapping("/rooms/{roomId}/reservations/input")
    public String input(@AuthenticationPrincipal UserDetailsImpl principal,
                        @PathVariable Integer roomId,
                        @ModelAttribute("reservationInputForm") @Valid ReservationInputForm form,
                        BindingResult binding,
                        RedirectAttributes ra,
                        HttpSession httpSession,
                        Model model) {

    	
        System.out.println("[INPUT] START roomId=" + roomId);

        // フォーム値
        System.out.println("[INPUT] form start=" + form.getStartDateTime()
                         + " end=" + form.getEndDateTime());
        
    	
        // 1) 基本バリデーション（必須、開始<終了 など）
        if (!binding.hasErrors()) {
            reservationInputValidator.validateWithRoomId(form, binding, roomId);
        }
        if (binding.hasErrors()) {
            // エラー時は rooms/show を再描画するため、画面に必要なモデルを詰め直す
        	 System.out.println("[INPUT] binding errors:");
        	    binding.getAllErrors().forEach(e -> System.out.println("  - " + e));
            populateRoomShowModel(roomId, model);
            return "rooms/show";
        }

        System.out.println("[INPUT] validation OK");
        // 2) 金額計算など予約確定前の集約（ReservationService側に任せる）
        LocalDateTime startAt = form.getStartDateTime();
        LocalDateTime endAt   = form.getEndDateTime();

        System.out.println("[INPUT] buildConfirmDto...");
        // ここはプロジェクトの既存APIに合わせてください
        // 例: reservationService.buildConfirmDto(roomId, principal.getUser(), startAt, endAt)
        ReservationConfirmDto dto = null;
        try {
            dto = reservationService.buildConfirmDto(roomId, principal.getUser(),
            		startAt, endAt);
            System.out.println("[INPUT] buildConfirmDto OK dto=" + dto);
        } catch (Exception e) {
            System.out.println("[INPUT][ERROR] buildConfirmDto threw " + e.getClass().getName()
                    + ": " + e.getMessage());
            e.printStackTrace(); // ★必ず出す
            ra.addFlashAttribute("errorMessage", "金額計算でエラーが発生しました。");
            return "redirect:/rooms/" + roomId;
        }

        System.out.println("[INPUT] dto created = " + dto);
        // 3) セッションにDTO保存 → /reservations/confirm へ
        httpSession.setAttribute("reservationDTO", dto);
        
        System.out.println("[INPUT] dto created = " + dto);
        return "redirect:/reservations/confirm";
    }
    
    private void populateRoomShowModel(Integer roomId, Model model) {
        var room = roomRepository.findById(roomId).orElse(null);
        model.addAttribute("room", room);

        // 営業時間
        var hours = roomBusinessHourRepository.findByRoomIdOrderByDayIndexAsc(roomId);
        model.addAttribute("businessHours", hours);

        // レビュー（公開）ページングは「最初のページ」を想定（必要ならパラメータ化）
        var reviewsPage = reviewRepository.findByRoomIdAndPublicVisibleTrueOrderByCreatedAtDesc(
                roomId, org.springframework.data.domain.PageRequest.of(0, 5, org.springframework.data.domain.Sort.by("createdAt").descending()));
        model.addAttribute("reviewsPage", reviewsPage);

        // 非公開だがホスト返信あり
        var hiddenWithReply = reviewRepository.findByRoomIdAndPublicVisibleFalseAndHostReplyIsNotNullOrderByHostReplyAtDesc(roomId);
        model.addAttribute("hiddenWithReply", hiddenWithReply);

        // 平均・件数（公開のみ）
        Double avgScore = reviewRepository.findAveragePublicScoreByRoomId(roomId);
        long reviewCount = reviewRepository.countByRoomIdAndPublicVisibleTrue(roomId);
        model.addAttribute("avgScore", avgScore == null ? 0.0 : avgScore);
        model.addAttribute("reviewCount", reviewCount);

        // 料金ルール（フラグメント表示用）
        var rules = priceRuleRepository.findByRoomId(roomId);
        var order = java.util.Comparator
                .comparing(com.example.studio_book.entity.PriceRule::getWeekday, ReservationController::nullFirstCompare)
                .thenComparing(com.example.studio_book.entity.PriceRule::getStartHour, ReservationController::nullFirstCompare)
                .thenComparing(com.example.studio_book.entity.PriceRule::getEndHour,   ReservationController::nullFirstCompare);

        var flatFeeRules = rules.stream()
                .filter(r -> "flat_fee".equals(r.getRuleType()) && r.getFlatFee() != null)
                .sorted(order)
                .toList();

        var base = java.math.BigDecimal.valueOf(room != null ? room.getPrice() : 0);
        var multiplierRules = rules.stream()
                .filter(r -> "multiplier".equals(r.getRuleType()) && r.getMultiplier() != null)
                .sorted(order)
                .map(r -> new com.example.studio_book.viewmodel.PriceRuleViewModel(
                        r, base.multiply(r.getMultiplier()).setScale(0, java.math.RoundingMode.HALF_UP)))
                .toList();

        model.addAttribute("flatFeeRules", flatFeeRules);
        model.addAttribute("multiplierRules", multiplierRules);
    }

    // null 先行コンパレータ（PriceRule並び替え用）
    private static <T extends Comparable<T>> int nullFirstCompare(T a, T b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }
}
