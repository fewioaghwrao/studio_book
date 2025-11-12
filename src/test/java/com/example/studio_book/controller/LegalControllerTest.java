// src/test/java/com/example/studio_book/controller/LegalControllerTest.java
package com.example.studio_book.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LegalController.class)
@AutoConfigureMockMvc(addFilters = false)   // ★ セキュリティフィルタを無効化
class LegalControllerTest {

    @Autowired
    MockMvc mvc;

    @Nested
    @DisplayName("/terms")
    class Terms {
        @Test
        @DisplayName("正常に表示できる & lastUpdated を含む")
        void terms_success() throws Exception {
            mvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(view().name("terms"))
                .andExpect(model().attributeExists("lastUpdated"));
        }
    }

    @Nested
    @DisplayName("/privacy")
    class Privacy {
        @Test
        @DisplayName("正常に表示できる & lastUpdated を含む")
        void privacy_success() throws Exception {
            mvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name("privacy"))
                .andExpect(model().attributeExists("lastUpdated"));
        }
    }

    @Nested
    @DisplayName("/legal/commerce")
    class Commerce {
        @Test
        @DisplayName("正常に表示できる & lastUpdated を含む")
        void commerce_success() throws Exception {
            mvc.perform(get("/legal/commerce"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal/commerce"))
                .andExpect(model().attributeExists("lastUpdated"));
        }
    }
}
