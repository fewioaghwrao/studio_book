// src/test/java/com/example/studio_book/controller/StripeWebhookControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.example.studio_book.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;

@WebMvcTest(StripeWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "stripe.webhook-secret=whsec_test")
class StripeWebhookControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    StripeService stripeService;

    private static final String URL = "/stripe/webhook";
    private static final String SIG = "t=123,v1=abc";
    private static final String PAYLOAD = "{\"id\":\"evt_123\",\"type\":\"dummy\"}";

    // ヘッダなし → 400
    @Test
    @DisplayName("署名ヘッダなし → 400 BAD_REQUEST(missing-signature)")
    void missingSignature_returns400() throws Exception {
        mvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYLOAD))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("missing-signature"));
    }

    // 検証失敗 → 400
    @Test
    @DisplayName("署名検証失敗 → 400 BAD_REQUEST(invalid-signature)")
    void invalidSignature_returns400() throws Exception {
        try (MockedStatic<Webhook> mocked = org.mockito.Mockito.mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(PAYLOAD, SIG, "whsec_test"))
                  .thenThrow(new SignatureVerificationException("bad sig", null));

            mvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", SIG)
                    .content(PAYLOAD))
               .andExpect(status().isBadRequest())
               .andExpect(content().string("invalid-signature"));
        }
        // サービスは呼ばれない
        org.mockito.Mockito.verify(stripeService, never()).processSessionCompleted(any(Event.class));
        org.mockito.Mockito.verify(stripeService, never()).processPaymentIntentSucceeded(any(Event.class));
        org.mockito.Mockito.verify(stripeService, never()).processChargeEvent(any(Event.class));
    }

    // checkout.session.completed
    @Test
    @DisplayName("checkout.session.completed → processSessionCompleted が呼ばれ 200 OK")
    void checkoutSessionCompleted_callsService() throws Exception {
        Event evt = new Event();
        evt.setType("checkout.session.completed");

        try (MockedStatic<Webhook> mocked = org.mockito.Mockito.mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(PAYLOAD, SIG, "whsec_test"))
                  .thenReturn(evt);

            mvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", SIG)
                    .content(PAYLOAD))
               .andExpect(status().isOk())
               .andExpect(content().string("ok"));
        }

        org.mockito.Mockito.verify(stripeService, times(1)).processSessionCompleted(evt);
        org.mockito.Mockito.verify(stripeService, never()).processPaymentIntentSucceeded(any(Event.class));
        org.mockito.Mockito.verify(stripeService, never()).processChargeEvent(any(Event.class));
    }

    // payment_intent.succeeded
    @Test
    @DisplayName("payment_intent.succeeded → processPaymentIntentSucceeded が呼ばれ 200 OK")
    void paymentIntentSucceeded_callsService() throws Exception {
        Event evt = new Event();
        evt.setType("payment_intent.succeeded");

        try (MockedStatic<Webhook> mocked = org.mockito.Mockito.mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(PAYLOAD, SIG, "whsec_test"))
                  .thenReturn(evt);

            mvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", SIG)
                    .content(PAYLOAD))
               .andExpect(status().isOk())
               .andExpect(content().string("ok"));
        }

        org.mockito.Mockito.verify(stripeService, times(1)).processPaymentIntentSucceeded(evt);
        org.mockito.Mockito.verify(stripeService, never()).processSessionCompleted(any(Event.class));
        org.mockito.Mockito.verify(stripeService, never()).processChargeEvent(any(Event.class));
    }

    // charge.succeeded
    @Test
    @DisplayName("charge.succeeded → processChargeEvent が呼ばれ 200 OK")
    void chargeSucceeded_callsService() throws Exception {
        Event evt = new Event();
        evt.setType("charge.succeeded");

        try (MockedStatic<Webhook> mocked = org.mockito.Mockito.mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(PAYLOAD, SIG, "whsec_test"))
                  .thenReturn(evt);

            mvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", SIG)
                    .content(PAYLOAD))
               .andExpect(status().isOk())
               .andExpect(content().string("ok"));
        }

        org.mockito.Mockito.verify(stripeService, times(1)).processChargeEvent(evt);
        org.mockito.Mockito.verify(stripeService, never()).processSessionCompleted(any(Event.class));
        org.mockito.Mockito.verify(stripeService, never()).processPaymentIntentSucceeded(any(Event.class));
    }

    // charge.updated
    @Test
    @DisplayName("charge.updated → processChargeEvent が呼ばれ 200 OK")
    void chargeUpdated_callsService() throws Exception {
        Event evt = new Event();
        evt.setType("charge.updated");

        try (MockedStatic<Webhook> mocked = org.mockito.Mockito.mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(PAYLOAD, SIG, "whsec_test"))
                  .thenReturn(evt);

            mvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Stripe-Signature", SIG)
                    .content(PAYLOAD))
               .andExpect(status().isOk())
               .andExpect(content().string("ok"));
        }

        org.mockito.Mockito.verify(stripeService, times(1)).processChargeEvent(evt);
        org.mockito.Mockito.verify(stripeService, never()).processSessionCompleted(any(Event.class));
        org.mockito.Mockito.verify(stripeService, never()).processPaymentIntentSucceeded(any(Event.class));
    }
}

