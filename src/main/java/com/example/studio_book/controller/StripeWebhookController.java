package com.example.studio_book.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.example.studio_book.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

@Controller
public class StripeWebhookController {
    private final StripeService stripeService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    public StripeWebhookController(StripeService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping(value = "/stripe/webhook", consumes = "application/json")
    public ResponseEntity<String> webhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        System.out.println("[WEBHOOK] HIT /stripe/webhook");
        System.out.println("[WEBHOOK] Stripe-Signature=" + sigHeader);
        System.out.println("[WEBHOOK] payload=" + payload);

        if (sigHeader == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("missing-signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            System.out.println("[WEBHOOK] signature verification failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid-signature");
        }

        System.out.println("[WEBHOOK] type=" + event.getType());

        if ("checkout.session.completed".equals(event.getType())) {
            stripeService.processSessionCompleted(event);
        } else if ("payment_intent.succeeded".equals(event.getType())) {
            stripeService.processPaymentIntentSucceeded(event);
        } else if ("charge.succeeded".equals(event.getType()) || "charge.updated".equals(event.getType())) {
            stripeService.processChargeEvent(event);  // ★ 追加
        }
        return ResponseEntity.ok("ok");
    }

}
