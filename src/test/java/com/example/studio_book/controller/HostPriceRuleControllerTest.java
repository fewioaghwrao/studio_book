// src/test/java/com/example/studio_book/controller/HostPriceRuleControllerTest.java
package com.example.studio_book.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.example.studio_book.entity.PriceRule;
import com.example.studio_book.entity.Room;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.RoomRepository;

@WebMvcTest(controllers = HostPriceRuleController.class)
@AutoConfigureMockMvc(addFilters = false)
class HostPriceRuleControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PriceRuleRepository priceRuleRepository;

    @MockBean
    RoomRepository roomRepository;

    private static final Integer ROOM_ID = 10;
    private static final Integer RULE_ID = 111;
    private static final RequestPostProcessor CSRF = csrf();

    private Room stubRoom() {
        Room r = new Room();
        r.setId(ROOM_ID);
        r.setName("RoomA");
        return r;
    }

    private PriceRule rule(Integer id, Integer roomId, String type, Integer weekday,
                           String start, String end, BigDecimal multiplier, Integer flatFee, String note) {
        PriceRule p = new PriceRule();
        p.setId(id);
        p.setRoomId(roomId);
        p.setRuleType(type);
        p.setWeekday(weekday);
        p.setStartHour(start == null ? null : LocalTime.parse(start));
        p.setEndHour(end == null ? null : LocalTime.parse(end));
        p.setMultiplier(multiplier);
        p.setFlatFee(flatFee);
        p.setNote(note);
        return p;
    }

    /* ---------- GET ---------- */
    @Test
    @DisplayName("GET /host/rooms/{roomId}/price-rules : 正常表示")
    void edit_get_success() throws Exception {
        given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubRoom()));
        given(priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(ROOM_ID))
                .willReturn(List.of(
                        rule(1, ROOM_ID, "multiplier", 1, "09:00", "12:00", new BigDecimal("1.50"), null, "morning"),
                        rule(2, ROOM_ID, "flat_fee", null, null, null, null, 1000, "all-day")
                ));

        mockMvc.perform(get("/host/rooms/{roomId}/price-rules", ROOM_ID)
                .with(csrf()))   // ★ 追加
            .andExpect(status().isOk())
            .andExpect(view().name("host/price_rules/edit"));
    }


    /* ---------- POST add(flat_fee) ---------- */
    @Nested
    class AddFlatFee {

        @Test
        @DisplayName("POST flat_fee 正常：保存")
        void add_flatFee_success() throws Exception {
            given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubRoom()));
            given(priceRuleRepository.existsByRoomIdAndRuleTypeAndWeekday(ROOM_ID, "flat_fee", null))
                    .willReturn(false);

            given(priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(ROOM_ID))
                    .willReturn(List.of(rule(5, ROOM_ID, "flat_fee", null, null, null, null, 2000, "note")));

            mockMvc.perform(post("/host/rooms/{roomId}/price-rules", ROOM_ID)
                    .with(CSRF)
                    .param("rows[0].ruleType", "flat_fee")
                    .param("rows[0].flatFee", "2000"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("host/price_rules/edit"))
                    .andExpect(model().attributeExists("saved"));

            then(priceRuleRepository).should().save(argThat(e ->
                    e.getRoomId().equals(ROOM_ID)
                            && "flat_fee".equals(e.getRuleType())
                            && e.getFlatFee().equals(2000)
                            && e.getMultiplier() == null
            ));
        }


        @Test
        @DisplayName("POST flat_fee 異常：重複")
        void add_flatFee_duplicate() throws Exception {
            given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubRoom()));
            given(priceRuleRepository.existsByRoomIdAndRuleTypeAndWeekday(ROOM_ID, "flat_fee", 1))
                    .willReturn(true);
            given(priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(ROOM_ID))
                    .willReturn(List.of());

            mockMvc.perform(post("/host/rooms/{roomId}/price-rules", ROOM_ID)
                    .with(CSRF)
                    .param("rows[0].ruleType", "flat_fee")
                    .param("rows[0].weekday", "1")
                    .param("rows[0].flatFee", "1200"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("host/price_rules/edit"));

            then(priceRuleRepository).should(never()).save(any());
        }
    }


    /* ---------- POST add(multiplier) ---------- */
    @Nested
    class AddMultiplier {

        @Test
        @DisplayName("POST multiplier 正常：保存")
        void add_multiplier_success() throws Exception {
            given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubRoom()));

            given(priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(ROOM_ID))
                    .willReturn(List.of(rule(7, ROOM_ID, "multiplier", 1, "09:00", "10:15", new BigDecimal("1.50"), null, null)));

            mockMvc.perform(post("/host/rooms/{roomId}/price-rules", ROOM_ID)
                    .with(CSRF)
                    .param("rows[0].ruleType", "multiplier")
                    .param("rows[0].weekday", "1")
                    .param("rows[0].startHour", "09:00")
                    .param("rows[0].endHour", "10:15")
                    .param("rows[0].multiplier", "1.50"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("host/price_rules/edit"))
                    .andExpect(model().attributeExists("saved"));

            then(priceRuleRepository).should().save(argThat(e ->
                    e.getRoomId().equals(ROOM_ID)
                            && "multiplier".equals(e.getRuleType())
                            && new BigDecimal("1.50").equals(e.getMultiplier())
                            && LocalTime.of(9, 0).equals(e.getStartHour())
            ));
        }
    }


    /* ---------- POST delete ---------- */
    @Nested
    class DeleteRule {

        @Test
        @DisplayName("POST delete 正常：room一致 → delete呼ばれる")
        void delete_ok() throws Exception {
            given(roomRepository.findById(ROOM_ID)).willReturn(Optional.of(stubRoom()));
            given(priceRuleRepository.findById(RULE_ID))
                    .willReturn(Optional.of(rule(RULE_ID, ROOM_ID, "flat_fee", null, null, null, null, 1000, null)));

            given(priceRuleRepository.findByRoomIdOrderByWeekdayAscStartHourAscIdAsc(ROOM_ID))
                    .willReturn(List.of());

            mockMvc.perform(post("/host/rooms/{roomId}/price-rules/{ruleId}/delete", ROOM_ID, RULE_ID)
                    .with(CSRF))
                    .andExpect(status().isOk())
                    .andExpect(view().name("host/price_rules/edit"))
                    .andExpect(model().attributeExists("deleted"));

            then(priceRuleRepository).should().deleteById(RULE_ID);
        }
    }
}

