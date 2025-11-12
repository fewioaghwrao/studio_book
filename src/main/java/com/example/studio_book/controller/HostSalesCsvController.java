// src/main/java/com/example/studio_book/controller/HostSalesCsvController.java
package com.example.studio_book.controller;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.datetime.standard.DateTimeFormatterFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.studio_book.dto.HostSalesRowProjection;
import com.example.studio_book.entity.ReservationChargeItem;
import com.example.studio_book.repository.ReservationChargeItemRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.security.UserDetailsImpl;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host")
public class HostSalesCsvController {

    private final ReservationRepository reservationRepository;
    private final ReservationChargeItemRepository chargeItemRepository;

    private static final DateTimeFormatter CSV_DT =
            new DateTimeFormatterFactory("yyyy-MM-dd HH:mm").createDateTimeFormatter();

    // ▼ 一覧のCSV（現在のフィルタをそのまま適用：roomId / onlyWithItems）
    @GetMapping(value = "/sales_details.csv", produces = "text/csv;charset=UTF-8")
    public void exportListCsv(@AuthenticationPrincipal UserDetailsImpl principal,
                              @RequestParam(required = false) Integer roomId,
                              @RequestParam(defaultValue = "true") boolean onlyWithItems,
                              HttpServletResponse resp) throws Exception {

        var hostId = principal.getUser().getId();
        int only = onlyWithItems ? 1 : 0;

        // 全件（ページングなし）で取得
        Pageable unpaged = PageRequest.of(0, Integer.MAX_VALUE);
        Page<HostSalesRowProjection> page =
                reservationRepository.findSalesDetailsForHost(hostId, only, roomId, unpaged);

        // レスポンスヘッダ
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"host-sales.csv\"");

        // UTF-8 BOM（Excel対策）
        var os = resp.getOutputStream();
        os.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});

        try (var writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {
            // ヘッダ
       /*     writer.println(String.join(",",
                    "reservation_id", "room_name", "guest_name", "start_at", "end_at", "amount", "status"));*/
                 writer.println(String.join(",",
            "予約ID", "スタジオ名", "予約者", "予約開始時刻", "予約終了日時", "総額(円)", "状態"));

            // 行
            for (var r : page.getContent()) {
                writer.println(String.join(",",
                        csv(r.getReservationId()),
                        csv(r.getRoomName()),
                        csv(r.getGuestName()),
                        csv(fmt(r.getStartAt())),
                        csv(fmt(r.getEndAt())),
                        csv(r.getAmount()),
                        csv(r.getStatus())
                ));
            }
        }
    }

    // ▼ 予約1件の明細CSV（reservation_charge_items を吐く）
    @GetMapping(value = "/sales_details/{id}/items.csv", produces = "text/csv;charset=UTF-8")
    public void exportItemsCsv(@AuthenticationPrincipal UserDetailsImpl principal,
                               @PathVariable Integer id,
                               HttpServletResponse resp) throws Exception {

        var hostId = principal.getUser().getId();

        // 権限チェックも兼ねてヘッダー1件取得（存在＋ホスト本人の部屋か）
        var headOpt = reservationRepository.findSalesHeadOne(hostId, id);
        var head = headOpt.orElseThrow(() -> new RuntimeException("not found or not yours"));

        List<ReservationChargeItem> items = chargeItemRepository.findByReservationIdOrderBySliceStartAsc(id);

        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"reservation-" + head.getReservationId() + "-items.csv\"");

        var os = resp.getOutputStream();
        os.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});

        try (var writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {
            // 先頭に予約ヘッダ情報をメタとして出す（Excelで見やすい）
          /*  writer.println("# reservation_id," + head.getReservationId());
            writer.println("# room_name," + esc(head.getRoomName()));
            writer.println("# guest_name," + esc(head.getGuestName()));
            writer.println("# period," + fmt(head.getStartAt()) + " 〜 " + fmt(head.getEndAt()));
            writer.println("# amount," + head.getAmount());
            writer.println();*/
        	writer.println("# 予約ID," + head.getReservationId());
            writer.println("# スタジオ名," + esc(head.getRoomName()));
            writer.println("# 予約者," + esc(head.getGuestName()));
            writer.println("# 期間," + fmt(head.getStartAt()) + " 〜 " + fmt(head.getEndAt()));
            writer.println("# 総額(円)," + head.getAmount());
            writer.println();

            // 明細ヘッダ
        /*    writer.println(String.join(",",
                    "kind", "description", "slice_start", "slice_end", "unit_rate_per_hour", "slice_amount"));*/
            writer.println(String.join(",",
                    "区分", "明細内容", "開始", "終了", "1時間当たりの値段", "金額(円)"));

            // 明細行
            for (var i : items) {
                writer.println(String.join(",",
                        csv(i.getKind()),
                        csv(i.getDescription()),
                        csv(fmt(i.getSliceStart())),
                        csv(fmt(i.getSliceEnd())),
                        csv(i.getUnitRatePerHour()),
                        csv(i.getSliceAmount())
                ));
            }
        }
    }

    // ===== CSV helper =====

    private static String fmt(LocalDateTime dt) {
        return dt == null ? "" : CSV_DT.format(dt);
    }

    // RFC4180風の簡易エスケープ：ダブルクォートで囲み、内部の " を "" に
    private static String esc(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

    private static String csv(Object o) {
        if (o == null) return "\"\"";
        return esc(String.valueOf(o));
    }
}

