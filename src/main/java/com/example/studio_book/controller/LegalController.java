package com.example.studio_book.controller;

import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {

    @GetMapping("/terms")
    public String terms(Model model) {
        model.addAttribute("lastUpdated", LocalDate.now().toString());
        return "terms";
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("lastUpdated", LocalDate.now().toString());
        return "privacy";
    }

    @GetMapping("/legal/commerce")
    public String commerce(Model model) {
        model.addAttribute("lastUpdated", LocalDate.now().toString());
        return "legal/commerce";
    }
}
