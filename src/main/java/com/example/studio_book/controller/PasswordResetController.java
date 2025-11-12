// controller/PasswordResetController.java
package com.example.studio_book.controller;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.studio_book.entity.PasswordResetToken;
import com.example.studio_book.repository.PasswordResetTokenRepository;
import com.example.studio_book.repository.UserRepository;
import com.example.studio_book.service.PasswordResetService;

import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/password")
public class PasswordResetController {

    private final PasswordResetService resetService;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/forgot")
    public String forgotForm() {
        return "password/forgot";
    }

    @PostMapping("/forgot")
    public String sendMail(@RequestParam String email, Model model) {
        try {
            resetService.sendResetMail(email);
            model.addAttribute("message", "パスワード再設定用のリンクをメールで送信しました。");
        } catch (MessagingException e) {
            model.addAttribute("error", "メール送信に失敗しました。");
        }
        return "password/forgot";
    }

    @GetMapping("/reset")
    public String resetForm(@RequestParam String token, Model model) {
        Optional<PasswordResetToken> opt = tokenRepository.findByToken(token);
        if (opt.isEmpty() || opt.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            model.addAttribute("error", "無効または期限切れのリンクです。");
            return "password/forgot";
        }
        model.addAttribute("token", token);
        return "password/reset";
    }

    @PostMapping("/reset")
    @Transactional
    public String resetPassword(@RequestParam String token,
            @RequestParam String password,
            @RequestParam String passwordConfirm,
            RedirectAttributes ra,
            Model model) {
        // ① 確認一致チェック
        if (!password.equals(passwordConfirm)) {
            model.addAttribute("error", "確認用パスワードが一致しません。");
            model.addAttribute("token", token);
            return "password/reset";
        }

        // ② トークン検証
        Optional<PasswordResetToken> opt = tokenRepository.findByToken(token);
        if (opt.isEmpty() || opt.get().getExpiresAt().isBefore(LocalDateTime.now())) {
            model.addAttribute("error", "無効または期限切れのリンクです。");
            return "password/forgot";
        }

        PasswordResetToken prt = opt.get();

        // ③ ユーザー取得（UserRepositoryのfindByEmailを使用）
        var user = userRepository.findByEmail(prt.getEmail());
        if (user == null) {
            model.addAttribute("error", "該当するユーザーが見つかりません。");
            return "password/forgot";
        }

        // ④ BCryptでハッシュ化して更新
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        // ⑤ トークン削除（再利用防止）
        tokenRepository.delete(prt);

        // 成功時：クエリではなくFlash属性を積む（1度だけ表示される）
        ra.addFlashAttribute("resetSuccess", true);
        return "redirect:/login";
}
}
