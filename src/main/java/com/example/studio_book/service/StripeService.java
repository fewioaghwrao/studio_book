package com.example.studio_book.service;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.studio_book.dto.ReservationConfirmDto;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.RoomRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.PermissionException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.Mode;
import com.stripe.param.checkout.SessionCreateParams.PaymentMethodType;
import com.stripe.param.checkout.SessionRetrieveParams;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;

@Service
public class StripeService {
    // 定数
    private static final PaymentMethodType PAYMENT_METHOD_TYPE = SessionCreateParams.PaymentMethodType.CARD;  // 決済方法
    private static final String CURRENCY = "jpy";  // 通貨
    private static final long QUANTITY = 1L;  // 数量
    private static final Mode MODE = SessionCreateParams.Mode.PAYMENT;  // 支払いモード
    // ★ 分単位で往復できるように書式を見直し
    private static final DateTimeFormatter DATE_TIME_FORMATTER  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"); // 日付のフォーマット

    // Stripeのシークレットキー
    @Value("${stripe.api-key}")
    private String stripeApiKey;    
    
    // 決済成功時のリダイレクト先URL 
    @Value("${stripe.success-url}")
    private String stripeSuccessUrl;

    // 決済キャンセル時のリダイレクト先URL
    @Value("${stripe.cancel-url}")
    private String stripeCancelUrl;  
    
    private final RoomRepository roomRepository;
    private final ReservationService reservationService;

    public StripeService(RoomRepository roomRepository, ReservationService reservationService) {
        this.roomRepository = roomRepository;
        this.reservationService = reservationService;
    }

    // 依存性の注入後に一度だけ実行するメソッド
    @PostConstruct
    private void init() {
        // Stripeのシークレットキーを設定する
        System.out.println("[StripeService] stripeApiKey=" 
                + (stripeApiKey == null ? "null" : "length=" + stripeApiKey.length()));
        Stripe.apiKey = stripeApiKey;
    }

    public String createStripeSession(ReservationConfirmDto reservationDTO, User user) {
        Optional<Room> optionalRoom = roomRepository.findById(reservationDTO.getRoomId());
        Room room = optionalRoom.orElseThrow(() -> new EntityNotFoundException("指定されたIDのスタジオが存在しません。"));

        // 商品名
        String roomName = room.getName();

        // ★ Stripe へ送る金額は「最小通貨単位」。JPYは整数（例: 12500円 → 12500）
        long unitAmount = reservationDTO.getAmount(); // ここは既に「円」単位の long の想定で OK

        // メタデータ（付随情報）
        String roomId = reservationDTO.getRoomId().toString();
        String userId = user.getId().toString();
        String startAt = reservationDTO.getStartAt().format(DATE_TIME_FORMATTER);
        String endAt = reservationDTO.getEndAt().format(DATE_TIME_FORMATTER);
        String amount = reservationDTO.getAmount().toString();
        String hourlyPrice = reservationDTO.getHourlyPrice().toString();
        String hours = String.valueOf(reservationDTO.getHours());

        // セッションに入れる支払い情報
        SessionCreateParams sessionCreateParams =
            SessionCreateParams.builder()
                .addPaymentMethodType(PAYMENT_METHOD_TYPE)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(roomName)
                                        .build())
                                .setUnitAmount(unitAmount)
                                .setCurrency(CURRENCY)
                                .build())
                        .setQuantity(QUANTITY)
                        .build())
                .setMode(MODE)
                .setSuccessUrl(stripeSuccessUrl)
                .setCancelUrl(stripeCancelUrl)
                // ★ PaymentIntent に予約メタデータを付与（サーバーのみがセット可能）
                .setPaymentIntentData(
                    SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("roomId", roomId)
                        .putMetadata("userId", userId)
                        .putMetadata("startAt", startAt)
                        .putMetadata("endAt", endAt)
                        .putMetadata("amount", amount)
                        .putMetadata("hourlyPrice", hourlyPrice)
                        .putMetadata("hours", hours)
                        .build()
                )
                .build();

        try {
            // Stripeに送信する支払い情報をセッションとして作成する
            Session session = Session.create(sessionCreateParams);

            // 作成したセッションのIDを返す
            return session.getId();
        } catch (RateLimitException e) {
            System.out.println("短時間のうちに過剰な回数のAPIコールが行われました。");
            return "";
        } catch (InvalidRequestException e) {
            System.out.println("APIコールのパラメーターが誤っているか、状態が誤っているか、方法が無効でした。");
            return "";
        } catch (PermissionException e) {
            System.out.println("このリクエストに使用されたAPIキーには必要な権限がありません。");
            return "";
        } catch (AuthenticationException e) {
            System.out.println("Stripeは、提供された情報では認証できません。");
            return "";
        } catch (ApiConnectionException e) {
            System.out.println("お客様のサーバーとStripeの間でネットワークの問題が発生しました。");
            return "";
        } catch (ApiException e) {
            System.out.println("Stripe側で問題が発生しました（稀な状況です）。");
            return "";
        } catch (StripeException e) {
            System.out.println("Stripeとの通信中に予期せぬエラーが発生しました。");
            return "";
        }
    }
    // セッションから予約情報を取得し、ReservationServiceクラスを介してデータベースに登録する
    public void processSessionCompleted(Event event) {
        var deser = event.getDataObjectDeserializer();

        // まずは通常ルート（従来通り）
        if (deser.getObject().isPresent()) {
            Session session = (Session) deser.getObject().get();
            handleSession(session);
        } else {
            // ★ フォールバック：raw JSON から sessionId を抜いて API で取り直す
            try {
                String raw = deser.getRawJson(); // null のこともある
                if (raw != null) {
                    JsonNode node = new ObjectMapper().readTree(raw);

                    // node が data.object の中身（= checkout.session）を直接指している場合と
                    // Event 全体の JSON の場合があるので両対応で id を探す
                    JsonNode idNode = node.get("id");
                    if (idNode == null || idNode.isNull()) {
                        idNode = node.path("data").path("object").get("id");
                    }

                    if (idNode != null && !idNode.isNull()) {
                        String sessionId = idNode.asText();
                        SessionRetrieveParams retrieveParams = SessionRetrieveParams.builder()
                                .addExpand("payment_intent")
                                .build();
                        Session session = Session.retrieve(sessionId, retrieveParams, null);
                        handleSession(session);
                    } else {
                        System.out.println("checkout.session.completed のフォールバック復元に失敗（id が取れない） raw=" + raw);
                    }
                } else {
                    System.out.println("checkout.session.completed のフォールバック復元に失敗（rawJson が null）");
                }
            } catch (Exception e) {
                System.out.println("checkout.session.completed フォールバック例外: " + e.getMessage());
            }
        }

        System.out.println("Stripe API Version: " + event.getApiVersion());
        System.out.println("stripe-java Version: " + Stripe.VERSION + ", stripe-java API Version: " + Stripe.API_VERSION);
    }

    /** 共通ハンドラ：Session（expand 済み）から予約登録 */
    private void handleSession(Session session) {
        try {
            PaymentIntent pi = session.getPaymentIntentObject();
            Map<String, String> md = pi.getMetadata();
            String paymentIntentId = pi.getId();
            Long paidAmount = pi.getAmount(); // JPYなら整数

            reservationService.createReservationFromStripe(
                    md,
                    paymentIntentId,
                    session.getId(),   // checkout_session_id
                    paidAmount
            );
            System.out.println("予約情報の登録処理が成功しました。(via checkout.session.completed)");
        } catch (Exception e) {
            System.out.println("handleSession 例外: " + e.getMessage());
        }
    }
    
    public void processChargeEvent(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresentOrElse(raw -> {
            Charge charge = (Charge) raw;
            try {
                Map<String, String> md = charge.getMetadata();      // ← ここに予約メタデータが入ってくる（ログで確認済み）
                String paymentIntentId = charge.getPaymentIntent(); // 冪等キー
                Long paidAmount = charge.getAmount();               // JPY は整数

                reservationService.createReservationFromStripe(md, paymentIntentId, null, paidAmount);
                System.out.println("予約情報の登録処理が成功しました。(via " + event.getType() + ")");
            } catch (Exception e) {
                System.out.println("processChargeEvent 例外: " + e.getMessage());
            }
        }, () -> System.out.println(event.getType() + " のデシリアライズに失敗"));
    }
    
    public void processPaymentIntentSucceeded(Event event) {
        event.getDataObjectDeserializer().getObject().ifPresentOrElse(raw -> {
            com.stripe.model.PaymentIntent pi = (com.stripe.model.PaymentIntent) raw;
            try {
                // メタデータ取得
                Map<String, String> md = pi.getMetadata();
                String paymentIntentId = pi.getId();
                Long paidAmount = pi.getAmount(); // JPY なら整数

                // セッションIDは無しでもOK（トレース不要なら null 可）
                reservationService.createReservationFromStripe(md, paymentIntentId, null, paidAmount);
                System.out.println("[WEBHOOK] payment_intent.succeeded -> reservation upserted");
            } catch (Exception e) {
                System.out.println("[WEBHOOK] pi.succeeded error: " + e.getMessage());
            }
        }, () -> System.out.println("[WEBHOOK] cannot deserialize payment_intent.succeeded"));
    }
}
