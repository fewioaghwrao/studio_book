// src/test/java/com/example/studio_book/controller/HostRoomControllerTest.java
package com.example.studio_book.controller;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ModelMap;

import com.example.studio_book.entity.PriceRule;
import com.example.studio_book.entity.Room;
import com.example.studio_book.entity.RoomBusinessHour;
import com.example.studio_book.entity.User;
import com.example.studio_book.repository.PriceRuleRepository;
import com.example.studio_book.repository.RoomBusinessHourRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.viewmodel.PriceRuleViewModel; 

@WebMvcTest(controllers = HostRoomController.class)
class HostRoomControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RoomRepository roomRepository;

    @MockBean
    PriceRuleRepository priceRuleRepository;

    @MockBean
    RoomBusinessHourRepository roomBusinessHourRepository;

    // ===== Helper =====

    private User host(int id) {
        User u = new User();
        u.setId(id);
        u.setName("Host" + id);
        u.setEmail("host" + id + "@test.com");
        u.setEnabled(true);
        return u;
    }

    private UserDetailsImpl principalOf(User u) {
        Collection<GrantedAuthority> auth = new ArrayList<>();
        auth.add(new SimpleGrantedAuthority("ROLE_HOST"));
        return new UserDetailsImpl(u, auth);
    }
    private Room room(int id, User owner, int price) {
        Room r = new Room();
        r.setId(id);
        r.setUser(owner);
        r.setPrice(price);
        r.setName("Room" + id);
        return r;
    }

    private PriceRule flatFee(Integer weekday, LocalTime start, LocalTime end, int feeYen) {
        PriceRule r = new PriceRule();
        r.setRuleType("flat_fee");
        r.setWeekday(weekday);
        r.setStartHour(start);
        r.setEndHour(end);
        r.setFlatFee(feeYen);
        return r;
    }

    private PriceRule multiplier(Integer weekday, LocalTime start, LocalTime end, double mul) {
        PriceRule r = new PriceRule();
        r.setRuleType("multiplier");
        r.setWeekday(weekday);
        r.setStartHour(start);
        r.setEndHour(end);
        r.setMultiplier(java.math.BigDecimal.valueOf(mul));
        return r;
    }

    // ===== Tests =====

    @Nested
    @DisplayName("GET /host/rooms (index)")
    class Index {

        @Test
        @DisplayName("keywordなし → userId検索")
        void index_withoutKeyword() throws Exception {
            User me = host(1);
            var principal = principalOf(me);

            Page<Room> page =
                new PageImpl<>(List.of(room(10, me, 3000)), PageRequest.of(0,10), 1);

            given(roomRepository.findByUser_Id(eq(me.getId()), any(Pageable.class)))
                .willReturn(page);

            var result = mvc.perform(get("/host/rooms").with(user(principal)))
            	    .andExpect(status().isOk())
            	    .andExpect(view().name("host/rooms/index"))
            	    .andExpect(model().attributeExists("roomPage"))
            	    .andExpect(model().attribute("keyword", nullValue()))          // ← こちらに変更（存在しなくてもOK）
            	    .andReturn();

            ModelMap model = result.getModelAndView().getModelMap();
            Page<?> modelPage = (Page<?>) model.get("roomPage");
            assertThat(modelPage.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("keywordあり → like検索")
        void index_withKeyword() throws Exception {
            User me = host(2);
            var principal = principalOf(me);

            Page<Room> page =
                new PageImpl<>(List.of(room(20, me, 3500)), PageRequest.of(0,10), 1);

            given(roomRepository.findByUser_IdAndNameContainingIgnoreCase(
                eq(me.getId()), eq("studio"), any(Pageable.class))
            ).willReturn(page);

            mvc.perform(get("/host/rooms").param("keyword"," studio ")
                    .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("host/rooms/index"))
                .andExpect(model().attributeExists("roomPage", "keyword"));
        }
    }

    @Nested
    @DisplayName("GET /host/rooms/{id} (show)")
    class Show {

        @Test
        @DisplayName("正常 → model に room, rules, businessHours が入る")
        void show_ok() throws Exception {
            User me = host(1);
            var principal = principalOf(me);

            Room room = room(100, me, 3000);
            given(roomRepository.findByIdAndUser_Id(100, me.getId()))
                .willReturn(Optional.of(room));

            // multiplier → 3000 * 1.5 = 4500
            PriceRule pf = flatFee(null, LocalTime.of(10,0), LocalTime.of(12,0), 500);
            PriceRule pm = multiplier(1, LocalTime.of(10,0), LocalTime.of(12,0), 1.5);

            given(priceRuleRepository.findByRoomId(100))
                .willReturn(List.of(pf, pm));

            // RoomBusinessHour dummy
            RoomBusinessHour h = new RoomBusinessHour();
            h.setId(1);
            h.setRoom(room);                         // <<<< 修正
            h.setDayIndex(1);
            h.setStartTime(LocalTime.of(9,0));
            h.setEndTime(LocalTime.of(18,0));
            h.setHoliday(false);
            given(roomBusinessHourRepository.findByRoomIdOrderByDayIndexAsc(100))
                .willReturn(List.of(h));

            var mv = mvc.perform(get("/host/rooms/{id}",100).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("host/rooms/show"))
                .andExpect(model().attributeExists("room","flatFeeRules","multiplierRules","businessHours"))
                .andReturn();

            ModelMap model = mv.getModelAndView().getModelMap();

            List<PriceRule> flatFeeRules = (List<PriceRule>) model.get("flatFeeRules");
            assertThat(flatFeeRules.get(0).getFlatFee()).isEqualTo(500);

            List<PriceRuleViewModel> mulVm = (List<PriceRuleViewModel>) model.get("multiplierRules");
            assertThat(mulVm.get(0).getAmount().intValue()).isEqualTo(4500);

            then(roomBusinessHourRepository)
                .should().findByRoomIdOrderByDayIndexAsc(100);
        }

        @Test
        @DisplayName("404 → NOT_FOUND")
        void notFound() throws Exception {
            User me = host(1);
            var principal = principalOf(me);

            given(roomRepository.findByIdAndUser_Id(999, me.getId()))
                .willReturn(Optional.empty());

            mvc.perform(get("/host/rooms/{id}",999).with(user(principal)))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /host/rooms/{id}/delete")
    class Delete {

        @Test
        @DisplayName("正常 → success redirect")
        void delete_ok() throws Exception {
            User me = host(1);
            var principal = principalOf(me);

            Room r = room(300, me, 4200);
            given(roomRepository.findByIdAndUser_Id(300, me.getId()))
                .willReturn(Optional.of(r));

            mvc.perform(post("/host/rooms/{id}/delete",300)
                    .with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/host/rooms?success"));

            then(roomRepository).should().delete(r);
        }

        @Test
        @DisplayName("404 → NOT_FOUND")
        void delete_notFound() throws Exception {
            User me = host(1);
            var principal = principalOf(me);

            given(roomRepository.findByIdAndUser_Id(404, me.getId()))
                .willReturn(Optional.empty());

            mvc.perform(post("/host/rooms/{id}/delete",404)
                    .with(user(principal)).with(csrf()))
                .andExpect(status().isNotFound());

            then(roomRepository).should(never()).delete(any());
        }
    }
}


