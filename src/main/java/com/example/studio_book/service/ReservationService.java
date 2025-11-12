package com.example.studio_book.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.studio_book.dto.ReservationConfirmDto;
import com.example.studio_book.entity.AdminSettings;
import com.example.studio_book.entity.AuditLog;
import com.example.studio_book.entity.Reservation;
import com.example.studio_book.entity.ReservationChargeItem;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.AdminSettingsRepository;
import com.example.studio_book.repository.AuditLogRepository;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.ReservationChargeItemRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PriceRuleRepository priceRuleRepository;
    
    private final AdminSettingsRepository adminSettingsRepository;           // ★追加
    private final ReservationChargeItemRepository chargeItemRepository;     // ★追加
    private final AuditLogRepository auditLogRepository;                    // ★追加


    public ReservationService(ReservationRepository reservationRepository,
                              RoomRepository roomRepository,
                              UserRepository userRepository,
                              PriceRuleRepository priceRuleRepository,
                              AdminSettingsRepository adminSettingsRepository,
                              ReservationChargeItemRepository chargeItemRepository,
                              AuditLogRepository auditLogRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.priceRuleRepository = priceRuleRepository;
        this.adminSettingsRepository = adminSettingsRepository;     // ★
        this.chargeItemRepository = chargeItemRepository;           // ★
        this.auditLogRepository = auditLogRepository;               // ★
    }

    private static final DateTimeFormatter MD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    /**
     * Webhook (checkout.session.completed) から予約を作成
     * @param md PaymentIntent.metadata
     * @param paymentIntentId PaymentIntent ID（重複防止キー）
     * @param checkoutSessionId Session ID（トレース用）
     * @param paidAmount Stripe が実際に課金した金額（JPY）
     */


    /** すでに存在チェック */
    public boolean existsByPaymentIntentId(String paymentIntentId) {
        return reservationRepository.existsByPaymentIntentId(paymentIntentId);
    }
    // 指定されたユーザーに紐づく予約を作成日時が新しい順に並べ替え、ページングされた状態で取得する
    public Page<Reservation> findReservationsByUserOrderByCreatedAtDesc(User user, Pageable pageable) {
        return reservationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }
    
    @Transactional
    public void createReservation(ReservationConfirmDto reservationconfirmDTO, User user) {
        Reservation reservation = new Reservation();

        Optional<Room> optionalRoom = roomRepository.findById(reservationconfirmDTO.getRoomId());
        Room room = optionalRoom.orElseThrow(() -> new EntityNotFoundException("指定されたIDのスタジオが存在しません。"));

        reservation.setRoom(room);
        reservation.setUser(user);
        reservation.setStartAt(reservationconfirmDTO.getStartAt());
        reservation.setEndAt(reservationconfirmDTO.getEndAt());
        reservation.setAmount(Math.toIntExact(reservationconfirmDTO.getAmount()));

        reservationRepository.save(reservation);
    }    
    
    @Transactional
    public void createReservationFromStripe(Map<String, String> md,
                                            String paymentIntentId,
                                            String checkoutSessionId,
                                            Long paidAmount) {

        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("payment_intent_id が空です");
        }
        if (existsByPaymentIntentId(paymentIntentId)) {
            // すでに登録済み（再送対策）
            return;
        }

        // 必須メタデータ
        Integer roomId = Integer.valueOf(md.get("roomId"));
        Integer userId = Integer.valueOf(md.get("userId"));
        LocalDateTime startAt = LocalDateTime.parse(md.get("startAt"), MD_FORMATTER);
        LocalDateTime endAt = LocalDateTime.parse(md.get("endAt"), MD_FORMATTER);

        // 金額は Stripe 側（paidAmount）を信頼。メタデータ amount は念のためのリファレンス。
        long amount = paidAmount != null ? paidAmount.longValue()
                                         : Long.parseLong(md.get("amount"));

        // 参照整合性
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found: " + roomId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // ここで必要なら重複・競合チェック（同時間帯に同室があればエラー等）
        // boolean overlap = reservationRepository.existsOverlap(roomId, startAt, endAt);
        // if (overlap) { ... }

        // 登録
        Reservation r = new Reservation();
        r.setRoom(room);
        r.setUser(user);
        r.setStartAt(startAt);
        r.setEndAt(endAt);
        r.setAmount((int) amount); // DB が INT 想定の場合
        r.setPaymentIntentId(paymentIntentId);
        r.setCheckoutSessionId(checkoutSessionId);
        r.setStatus("booked");

        reservationRepository.save(r);
        

        // ★ 料金内訳生成（admin_settings と price_rules 参照）
        generateChargeItemsAndAudit(r, paidAmount);

        // ★ 監査ログ（予約作成）
        auditLogRepository.save(
            AuditLog.builder()
                .ts(LocalDateTime.now())
                .actorId(r.getUser().getId())
                .action("reservation_created")
                .entity("reservation")
                .entityId(r.getId())
                .note("PI=" + paymentIntentId + ", CS=" + checkoutSessionId)
                .build()
        );
    }
    
    @Transactional(readOnly = true)
    public ReservationConfirmDto buildConfirmDto(Integer roomId, User user,
                                                 LocalDateTime startAt, LocalDateTime endAt) {
        if (roomId == null) throw new IllegalArgumentException("roomId is null");
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("start/end is invalid");
        }
        System.out.println("[BUILD] START roomId=" + roomId + " userId=" + user.getId()
                + " start=" + startAt + " end=" + endAt);

        // 1) ルーム取得
        var room = roomRepository.findById(roomId).orElse(null);
        System.out.println("[BUILD] after roomRepository.findById -> room=" + (room != null));
        if (room == null) throw new IllegalArgumentException("Room not found: " + roomId);

        // 2) ルール取得
        var rules = priceRuleRepository.findByRoomId(roomId);

        // 3) 料金内訳リスト
        var items = new java.util.ArrayList<com.example.studio_book.viewmodel.ConfirmLineItem>();

        // 4) 基本料金（分課金）
        BigDecimal basePerHour = BigDecimal.valueOf(room.getPrice()); // 円/時
        long minutesAll = Duration.between(startAt, endAt).toMinutes();
        long hoursRoundedUp = (minutesAll + 59) / 60; // 画面表示用の「時間数」
        BigDecimal baseAmount = basePerHour
                .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(minutesAll))
                .setScale(0, RoundingMode.HALF_UP);

        items.add(new com.example.studio_book.viewmodel.ConfirmLineItem(
                "base",
                "基本料金 (" + room.getPrice() + "円/時, " + minutesAll + "分)",
                baseAmount.longValue(),
                startAt, endAt
        ));

        // 5) 日毎の固定費・加算料金
        BigDecimal rulesTotal = BigDecimal.ZERO;

        LocalDate d = startAt.toLocalDate();
        LocalDate last = endAt.minusNanos(1).toLocalDate();

        while (!d.isAfter(last)) {
            final LocalDate d0 = d;
            LocalDateTime segStart = d.equals(startAt.toLocalDate()) ? startAt : d.atStartOfDay();
            LocalDateTime segEnd   = d.equals(endAt.toLocalDate())   ? endAt   : d.plusDays(1).atStartOfDay();

            int weekday1to7 = d.getDayOfWeek().getValue(); // 1=月..7=日

            // 5-1) 固定費（その日分を合算）
            BigDecimal flat = rules.stream()
                    .filter(rp -> "flat_fee".equals(rp.getRuleType()) && rp.getFlatFee() != null)
                    .filter(rp -> rp.getWeekday() == null || equalsWeekday(rp.getWeekday(), weekday1to7))
                    .map(rp -> BigDecimal.valueOf(rp.getFlatFee()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (flat.signum() > 0) {
                rulesTotal = rulesTotal.add(flat);
                items.add(new com.example.studio_book.viewmodel.ConfirmLineItem(
                        "flat_fee",
                        "固定費 (" + d + ")",
                        flat.longValue(),
                        segStart, segEnd
                ));
            }

            // 5-2) 加算（倍率×基本単価）重複分のみ
            for (var pr : rules) {
                if (!"multiplier".equals(pr.getRuleType()) || pr.getMultiplier() == null) continue;
                if (pr.getWeekday() != null && !equalsWeekday(pr.getWeekday(), weekday1to7)) continue;

                LocalTime winStart = pr.getStartHour() != null ? pr.getStartHour() : LocalTime.MIN;
                LocalTime winEnd   = pr.getEndHour()   != null ? pr.getEndHour()   : LocalTime.MIDNIGHT;
                LocalDateTime wStart = d0.atTime(winStart);
                LocalDateTime wEnd   = winEnd.equals(LocalTime.MIDNIGHT) ? d0.plusDays(1).atStartOfDay() : d0.atTime(winEnd);

                long ovMin = overlapMinutes(segStart, segEnd, wStart, wEnd);
                if (ovMin <= 0) continue;

                BigDecimal extraPerHour = basePerHour.multiply(pr.getMultiplier());
                BigDecimal extra = extraPerHour
                        .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(ovMin))
                        .setScale(0, RoundingMode.HALF_UP);

                if (extra.signum() > 0) {
                    rulesTotal = rulesTotal.add(extra);
                    items.add(new com.example.studio_book.viewmodel.ConfirmLineItem(
                            "multiplier",
                            "時間帯加算 (" + pr.getMultiplier() + "x, " + ovMin + "分, " + d + ")",
                            extra.longValue(),
                            // 重複区間
                            segStart.isAfter(wStart) ? segStart : wStart,
                            segEnd.isBefore(wEnd) ? segEnd : wEnd
                    ));
                }
            }

            d = d.plusDays(1);
        }

        // 6) 小計・税・合計（税率は admin_settings.tax_rate：0.1=10% を想定）
        BigDecimal subtotalBD = baseAmount.add(rulesTotal).setScale(0, RoundingMode.HALF_UP);

        BigDecimal taxRate = getDecimal("tax_rate", BigDecimal.ZERO); // 0.1=10%
        BigDecimal taxBD = taxRate.signum() > 0
                ? subtotalBD.multiply(taxRate).setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        if (taxBD.signum() > 0) {
            items.add(new com.example.studio_book.viewmodel.ConfirmLineItem(
                    "tax",
                    "消費税 (" + taxRate.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%)",
                    taxBD.longValue(),
                    null, null
            ));
        }

        BigDecimal totalBD = subtotalBD.add(taxBD);

        // 7) DTOに詰める（amount は最終合計＝Stripeへ送る額）
        return ReservationConfirmDto.builder()
                .roomId(room.getId())
                .roomName(room.getName())
                .startAt(startAt)
                .endAt(endAt)
                .hourlyPrice(room.getPrice())
                .hours(hoursRoundedUp)
                .items(items)
                .subtotal(subtotalBD.longValue())
                .tax(taxBD.longValue())
                .amount(totalBD.longValue())
                .build();
    }


    /** PriceRule.weekday の定義に合わせるユーティリティ
     *  例: DBが 0=日..6=土 の場合 → 1=月..7=日 に合わせて正規化
     */
    private boolean equalsWeekday(Integer ruleWeekday, int weekday1to7) {
        // 例）DB: 0(日)～6(土) のとき
        // int db0to6 = (weekday1to7 % 7); // 1..7 → 0..6 (1=月→1, …, 7=日→0)
        // return ruleWeekday != null && ruleWeekday == db0to6;

        // もし DB も 1..7 (月..日) ならそのままでOK:
        return ruleWeekday != null && ruleWeekday == weekday1to7;
    }

    /** 半開区間 [aStart, aEnd) と [bStart, bEnd) の重複分（分） */
    private long overlapMinutes(LocalDateTime aStart, LocalDateTime aEnd,
                                LocalDateTime bStart, LocalDateTime bEnd) {
        var s = aStart.isAfter(bStart) ? aStart : bStart;
        var e = aEnd.isBefore(bEnd) ? aEnd : bEnd;
        if (!s.isBefore(e)) return 0L;
        return java.time.Duration.between(s, e).toMinutes();
    }
    
    private void generateChargeItemsAndAudit(Reservation r, Long paidAmount) {
        final Integer reservationId = r.getId();
        final Room room = r.getRoom();
        final LocalDateTime startAt = r.getStartAt();
        final LocalDateTime endAt   = r.getEndAt();

        // ========== AdminSettings ==========
        // tax_rate は「0.1 → 10%」の小数表記を想定。/100 では割らない。
        BigDecimal taxRate = getDecimal("tax_rate", BigDecimal.ZERO); 
        boolean enableRules = getBoolean("ENABLE_PRICE_RULES", true);
        int billingUnitMin = getInt("BILLING_UNIT_MINUTES", 1);

        // ========== Base ==========
        BigDecimal basePerHour = BigDecimal.valueOf(room.getPrice()); // 円/時
        long totalMinutes = Duration.between(startAt, endAt).toMinutes();
        if (billingUnitMin > 1) {
            long units = (totalMinutes + billingUnitMin - 1) / billingUnitMin; // 切り上げ
            totalMinutes = units * billingUnitMin;
        }
        BigDecimal baseAmount = basePerHour
            .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(totalMinutes))
            .setScale(0, RoundingMode.HALF_UP);

        saveItem(reservationId, "base",
                "基本料金 (" + basePerHour.intValue() + "円/時, " + totalMinutes + "分)",
                baseAmount.intValue(),
                startAt, endAt, basePerHour.intValue());

        // ========== Rules ==========
        BigDecimal rulesTotal = BigDecimal.ZERO;
        if (enableRules) {
            var rules = priceRuleRepository.findByRoomId(room.getId());
            LocalDate d = startAt.toLocalDate();
            LocalDate last = endAt.minusNanos(1).toLocalDate();

            while (!d.isAfter(last)) {
                final LocalDate d0 = d;
                LocalDateTime segStart = d.equals(startAt.toLocalDate()) ? startAt : d.atStartOfDay();
                LocalDateTime segEnd   = d.equals(endAt.toLocalDate())   ? endAt   : d.plusDays(1).atStartOfDay();
                int weekday1to7 = d.getDayOfWeek().getValue();

                // flat_fee（その日分を一括）
                BigDecimal flat = rules.stream()
                    .filter(rp -> "flat_fee".equals(rp.getRuleType()) && rp.getFlatFee() != null)
                    .filter(rp -> rp.getWeekday() == null || equalsWeekday(rp.getWeekday(), weekday1to7))
                    .map(rp -> BigDecimal.valueOf(rp.getFlatFee()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (flat.signum() > 0) {
                    rulesTotal = rulesTotal.add(flat);
                    saveItem(reservationId, "flat_fee",
                            "固定費 (" + d + ")",
                            flat.intValue(),
                            segStart, segEnd, null);
                }

                // multiplier（ウィンドウ重複分）
                for (var pr : rules) {
                    if (!"multiplier".equals(pr.getRuleType()) || pr.getMultiplier() == null) continue;
                    if (pr.getWeekday() != null && !equalsWeekday(pr.getWeekday(), weekday1to7)) continue;

                    LocalTime winStart = pr.getStartHour() != null ? pr.getStartHour() : LocalTime.MIN;
                    LocalTime winEnd   = pr.getEndHour()   != null ? pr.getEndHour()   : LocalTime.MIDNIGHT;
                    LocalDateTime wStart = d0.atTime(winStart);
                    LocalDateTime wEnd   = winEnd.equals(LocalTime.MIDNIGHT) ? d0.plusDays(1).atStartOfDay() : d0.atTime(winEnd);

                    long ovMin = overlapMinutes(segStart, segEnd, wStart, wEnd);
                    if (ovMin <= 0) continue;

                    BigDecimal extraPerHour = basePerHour.multiply(pr.getMultiplier());
                    BigDecimal extra = extraPerHour
                        .divide(BigDecimal.valueOf(60), 10, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(ovMin))
                        .setScale(0, RoundingMode.HALF_UP);

                    if (extra.signum() > 0) {
                        rulesTotal = rulesTotal.add(extra);
                        saveItem(reservationId, "multiplier",
                                "時間帯加算 (" + pr.getMultiplier() + "x, " + ovMin + "分, " + d + ")",
                                extra.intValue(),
                                max(segStart, wStart), min(segEnd, wEnd),
                                extraPerHour.intValue());
                    }
                }

                d = d.plusDays(1);
            }
        }

        // ========== 小計 → 税 → 合計 ==========
        BigDecimal subtotal = baseAmount.add(rulesTotal);   // ★ 先に定義
        BigDecimal tax = BigDecimal.ZERO;
        if (taxRate.signum() > 0) {
            // tax_rate は 0.1 = 10% として扱う。/100 はしない。
            tax = subtotal.multiply(taxRate).setScale(0, RoundingMode.HALF_UP);

            saveItem(reservationId, "tax",
                    "消費税 (" + formatPercent(taxRate) + ")",
                    tax.intValue(),
                    null, null, null);
        }

        BigDecimal calcTotal = subtotal.add(tax);

        // 監査ログ：計算金額と実際の決済額の突合を記録
        String note = "calcTotal=" + calcTotal.intValue() + ", paid=" + (paidAmount != null ? paidAmount : -1);
        auditLogRepository.save(
            AuditLog.builder()
                .ts(LocalDateTime.now())
                .actorId(r.getUser().getId())
                .action("charge_items_generated")
                .entity("reservation")
                .entityId(reservationId)
                .note(note)
                .build()
        );
    }

    // 税率の表示用（0.1 → "10%"）
    private String formatPercent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100))
                   .setScale(0, RoundingMode.HALF_UP)
                   .toPlainString() + "%";
    }
    
    private void saveItem(Integer reservationId, String kind, String desc,
            Integer amount, LocalDateTime sliceStart, LocalDateTime sliceEnd,
            Integer unitRatePerHour) {
ReservationChargeItem it = new ReservationChargeItem();
it.setReservationId(reservationId);
it.setKind(kind);
it.setDescription(desc);
it.setSliceAmount(amount);
it.setSliceStart(sliceStart);
it.setSliceEnd(sliceEnd);
it.setUnitRatePerHour(unitRatePerHour);
chargeItemRepository.save(it);
}

private LocalDateTime max(LocalDateTime a, LocalDateTime b) { return a.isAfter(b) ? a : b; }
private LocalDateTime min(LocalDateTime a, LocalDateTime b) { return a.isBefore(b) ? a : b; }

private BigDecimal getDecimal(String key, BigDecimal def) {
    return adminSettingsRepository.findByKey(key)
        .map(AdminSettings::getValue)
        .map(v -> {
            try { return new BigDecimal(v); }
            catch (Exception e) { return def; }
        }).orElse(def);
}

private boolean getBoolean(String key, boolean def) {
return adminSettingsRepository.findByKey(key)
.map(AdminSettings::getValue)
.map(v -> v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("on"))
.orElse(def);
}

private int getInt(String key, int def) {
return adminSettingsRepository.findByKey(key)
.map(AdminSettings::getValue)
.map(v -> {
  try { return Integer.parseInt(v); } catch (Exception e) { return def; }
}).orElse(def);
}



    
}
