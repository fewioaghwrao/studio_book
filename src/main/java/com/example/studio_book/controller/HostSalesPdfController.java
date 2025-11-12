// src/main/java/com/example/studio_book/controller/HostSalesPdfController.java
package com.example.studio_book.controller;

import java.awt.Color;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.studio_book.entity.ReservationChargeItem;
import com.example.studio_book.repository.ReservationChargeItemRepository;
import com.example.studio_book.repository.ReservationRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/host")
public class HostSalesPdfController {

    private final ReservationRepository reservationRepository;
    private final ReservationChargeItemRepository chargeItemRepository;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final NumberFormat CURRENCY = NumberFormat.getIntegerInstance(Locale.JAPAN);

    @GetMapping(value = "/sales_details/{id}/invoice.pdf", produces = "application/pdf")
    public void invoicePdf(@AuthenticationPrincipal UserDetailsImpl principal,
                           @PathVariable Integer id,
                           HttpServletResponse resp) throws Exception {

        var hostId = principal.getUser().getId();

        // 予約ヘッダ（本人チェック兼ねる）
        var head = reservationRepository.findSalesHeadOne(hostId, id)
                .orElseThrow(() -> new RuntimeException("not found or not yours"));

        // 明細
        List<ReservationChargeItem> items =
                chargeItemRepository.findByReservationIdOrderBySliceStartAsc(id);

        // PDFレスポンス
        resp.setContentType("application/pdf");
        resp.setHeader("Content-Disposition",
                "attachment; filename=\"reservation-" + head.getReservationId() + "-invoice.pdf\"");

        var os = resp.getOutputStream();

        // ====== フォント準備（日本語埋め込み）======
        BaseFont bfRegular = loadBaseFont("/fonts/NotoSansJP-Regular.ttf");
        BaseFont bfBold    = loadBaseFont("/fonts/NotoSansJP-Bold.ttf");

        Font fTitle   = new Font(bfBold,   16);
        Font fLabel   = new Font(bfBold,   10);
        Font fText    = new Font(bfRegular,10);
        Font fSmall   = new Font(bfRegular,9);
        Font fTh      = new Font(bfBold,   10, Font.NORMAL, Color.WHITE);

        // ====== ドキュメント / ページ設定 ======
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(doc, os);
        doc.open();

        // ====== ヘッダ部 ======
        Paragraph title = new Paragraph("売上明細（請求書）", fTitle);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        doc.add(new Paragraph("発行日: " + formatNow(), fSmall));
        doc.add(Chunk.NEWLINE);

        // 会社や発行元（任意：必要に応じて固定文言を）
        PdfPTable issuer = new PdfPTable(new float[]{1f, 2f});
        issuer.setWidthPercentage(60);
        issuer.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        issuer.addCell(new Phrase("発行元", fLabel));
        issuer.addCell(new Phrase("N.O.（サンプル）", fText));
        issuer.addCell(new Phrase("連絡先", fLabel));
        issuer.addCell(new Phrase("support@example.com / 03-1234-5678", fText));
        doc.add(issuer);

        doc.add(Chunk.NEWLINE);

        // 予約情報
        PdfPTable meta = new PdfPTable(new float[]{1f, 2.5f, 1f, 2.5f});
        meta.setWidthPercentage(100);
        meta.getDefaultCell().setPadding(6);

        addMetaRow(meta, "予約ID", String.valueOf(head.getReservationId()),
                "状態", statusLabel(head.getStatus()), fLabel, fText);
        addMetaRow(meta, "スタジオ", head.getRoomName(), "予約者", head.getGuestName(), fLabel, fText);
        addMetaRow(meta, "利用開始時刻", fmt(head.getStartAt()), "利用終了時刻", fmt(head.getEndAt()), fLabel, fText);
        addMetaRow(meta, "総額（税込）", currency(head.getAmount()) + " 円", "", "", fLabel, fText);
        doc.add(meta);

        doc.add(Chunk.NEWLINE);

        // ====== 明細テーブル ======
        PdfPTable table = new PdfPTable(new float[]{3.0f, 2.2f, 2.2f, 1.5f, 1.5f});
        table.setWidthPercentage(100);

        // ヘッダ行
//        addTh(table, "区分", fTh);
        addTh(table, "明細内容", fTh);
        addTh(table, "利用開始", fTh);
        addTh(table, "利用終了", fTh);
        addTh(table, "単価", fTh);
        addTh(table, "金額", fTh);

        int itemsTotal = 0;
        for (var it : items) {
            itemsTotal += n(it.getSliceAmount());

//            table.addCell(new Phrase(z(it.getKind()), fText));
            table.addCell(new Phrase(z(it.getDescription()), fText));
            table.addCell(new Phrase(z(fmt(it.getSliceStart())), fText));
            table.addCell(new Phrase(z(fmt(it.getSliceEnd())), fText));
            table.addCell(right(z(it.getUnitRatePerHour() == null ? "-" : currency(it.getUnitRatePerHour()) + " 円"), fText));
            table.addCell(right(currency(n(it.getSliceAmount())) + " 円", fText));
        }
        doc.add(table);

        doc.add(Chunk.NEWLINE);

        // ====== サマリ（小計など） ======
        PdfPTable totals = new PdfPTable(new float[]{6.4f, 1.6f});
        totals.setWidthPercentage(100);

        PdfPCell left = new PdfPCell(new Phrase("備考：金額は税込・手数料込みです。", fSmall));
        left.setBorder(Rectangle.NO_BORDER);
        left.setPadding(4);
        totals.addCell(left);

        PdfPCell right1 = new PdfPCell(new Phrase("明細合計  " + currency(itemsTotal) + " 円", fLabel));
        right1.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right1.setBorder(Rectangle.NO_BORDER);
        right1.setPadding(4);
        totals.addCell(right1);

        PdfPCell right2 = new PdfPCell(new Phrase("予約合計  " + currency(head.getAmount()) + " 円", fLabel));
        right2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        right2.setBorder(Rectangle.NO_BORDER);
        right2.setPadding(4);
        totals.addCell(new PdfPCell(new Phrase("", fSmall)) {{ setBorder(Rectangle.NO_BORDER); }});
        totals.addCell(right2);

        doc.add(totals);

        // フッター
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("本書はシステムにより自動生成されています。", fSmall));

        doc.close();
    }

    // ===== helpers =====

    private static BaseFont loadBaseFont(String classpath) throws Exception {
        ClassPathResource res = new ClassPathResource(classpath);
        try (InputStream in = res.getInputStream()) {
            // InputStream → 一時ファイル経由で BaseFont 生成（Jar内でも安定）
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("font-", ".ttf");
            java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return BaseFont.createFont(tmp.toString(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        }
    }

    private static void addMetaRow(PdfPTable meta, String k1, String v1, String k2, String v2, Font fL, Font fT) {
        PdfPCell c1 = new PdfPCell(new Phrase(k1, fL));
        PdfPCell c2 = new PdfPCell(new Phrase(z(v1), fT));
        PdfPCell c3 = new PdfPCell(new Phrase(k2, fL));
        PdfPCell c4 = new PdfPCell(new Phrase(z(v2), fT));
        for (PdfPCell c : new PdfPCell[]{c1, c2, c3, c4}) {
            c.setPadding(6);
        }
        meta.addCell(c1); meta.addCell(c2); meta.addCell(c3); meta.addCell(c4);
    }

    private static void addTh(PdfPTable t, String text, Font f) {
        PdfPCell th = new PdfPCell(new Phrase(text, f));
        th.setHorizontalAlignment(Element.ALIGN_CENTER);
        th.setBackgroundColor(new Color(60, 60, 60));
        th.setPadding(6);
        t.addCell(th);
    }

    private static String formatNow() {
        return DateTimeFormatter.ofPattern("yyyy/MM/dd").format(LocalDateTime.now());
    }

    private static String fmt(LocalDateTime dt) {
        return dt == null ? "-" : DT.format(dt);
    }

    private static String z(String s) {
        return s == null ? "-" : s;
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }

    private static String currency(int v) {
        return CURRENCY.format(v);
    }

    private static PdfPCell right(String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setPadding(6);
        return c;
    }
    
    private static String statusLabel(String s) {
        if (s == null) return "-";
        return switch (s) {
            case "booked"   -> "予約済み";
            case "paid"     -> "利用済み";
            case "canceled" -> "キャンセル済み";
            default -> s;  // 未知の値はそのまま
        };
    }

}
