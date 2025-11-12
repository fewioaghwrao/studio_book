package com.example.studio_book.controller;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.studio_book.entity.User;
import com.example.studio_book.security.UserDetailsImpl;
import com.example.studio_book.service.UserService;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping
    public String index(@RequestParam(name = "keyword", required = false) String keyword,
                        @PageableDefault(page = 0, size = 10, sort = "id", direction = Direction.ASC) Pageable pageable,
                        Model model)
    {
        Page<User> userPage;

        if (keyword != null && !keyword.isEmpty()) {
            userPage = userService.findUsersByNameLikeOrFuriganaLike(keyword, keyword, pageable);
        } else {
            userPage = userService.findAllUsers(pageable);
        }

        model.addAttribute("userPage", userPage);
        model.addAttribute("keyword", keyword);

        return "admin/users/index";
    }
    
    @GetMapping("/{id}")
    public String show(@PathVariable(name = "id") Integer id, RedirectAttributes redirectAttributes, Model model) {
        Optional<User> optionalUser  = userService.findUserById(id);

        if (optionalUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "ユーザーが存在しません。");

            return "redirect:/admin/users";
        }

        User user = optionalUser.get();
        model.addAttribute("user", user);

        return "admin/users/show";
    }    
    
    @PostMapping("/{id}/enabled")
    public String updateEnabled(@PathVariable Integer id,
                                @RequestParam("enabled") boolean enabled,
                                @AuthenticationPrincipal UserDetailsImpl loginUser,   // ★追加
                                RedirectAttributes redirectAttributes) {

        var optionalUser = userService.findUserById(id);
        if (optionalUser.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "ユーザーが存在しません。");
            return "redirect:/admin/users";
        }

        var target = optionalUser.get();

        if (target.getRole() != null && Integer.valueOf(3).equals(target.getRole().getId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "管理者アカウントは有効/無効を変更できません。");
            return "redirect:/admin/users";
        }

        try {
            Integer actorId = (loginUser != null && loginUser.getUser() != null) ? loginUser.getUser().getId() : null;
            userService.setEnabled(target, enabled, actorId);  // ★実行者IDを渡す
            redirectAttributes.addFlashAttribute(
                "successMessage",
                String.format("ユーザー(ID=%d)を「%s」にしました。", id, enabled ? "有効" : "無効")
            );
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "更新に失敗しました。");
        }

        return "redirect:/admin/users";
    }
}
