// service/PasswordResetService.java
package com.example.studio_book.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.studio_book.entity.PasswordResetToken;
import com.example.studio_book.repository.PasswordResetTokenRepository;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final JavaMailSender mailSender;

    public void sendResetMail(String email) throws MessagingException {
        // 古いトークン削除
        tokenRepository.deleteByEmail(email);

        // 新しいトークン発行
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setEmail(email);
        resetToken.setToken(token);
        resetToken.setExpiresAt(LocalDateTime.now().plusHours(1));
        tokenRepository.save(resetToken);

        // メール送信
        String resetLink = "http://localhost:8080/password/reset?token=" + token;

        var message = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setTo(email);
        helper.setSubject("【スタジオ予約管理】パスワード再設定のご案内");
        helper.setText(
            "<p>以下のリンクからパスワード再設定を行ってください（有効期限1時間）:</p>"
          + "<p><a href='" + resetLink + "'>パスワードを再設定する</a></p>",
          true
        );

        mailSender.send(message);
    }
}

