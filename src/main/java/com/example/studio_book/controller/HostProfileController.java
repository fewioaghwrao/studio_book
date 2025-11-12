// src/main/java/com/example/studio_book/controller/HostProfileController.java
package com.example.studio_book.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.studio_book.entity.User;
import com.example.studio_book.form.UserEditForm;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.UserService;

@Controller
@RequestMapping("/host")
public class HostProfileController {
    private final UserService userService;
    public HostProfileController(UserService userService) { this.userService = userService; }

    @GetMapping
    public String index(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        User user = principal.getUser();
        model.addAttribute("user", user);
        return "host/index";
    }

    @GetMapping("/edit")
    public String edit(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        User u = principal.getUser();
        UserEditForm form = new UserEditForm(u.getName(), u.getFurigana(), u.getPostalCode(),
                u.getAddress(), u.getPhoneNumber(), u.getEmail());
        model.addAttribute("userEditForm", form);
        return "host/edit";
    }

    @PostMapping("/update")
    public String update(@ModelAttribute @Validated UserEditForm form,
                         BindingResult br,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         RedirectAttributes ra,
                         Model model) {
        User user = principal.getUser();

        if (userService.isEmailChanged(form, user) && userService.isEmailRegistered(form.getEmail())) {
            br.addError(new FieldError(br.getObjectName(), "email", "すでに登録済みのメールアドレスです。"));
        }
        if (br.hasErrors()) {
            model.addAttribute("userEditForm", form);
            return "host/edit";
        }
        userService.updateUser(form, user);
        ra.addFlashAttribute("successMessage", "会員情報を編集しました。");
        return "redirect:/host";
    }
}

