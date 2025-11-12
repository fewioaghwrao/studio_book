// ReviewController.java
package com.example.studio_book.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.studio_book.entity.Review;
import com.example.studio_book.entity.Room;
import com.example.studio_book.form.ReviewForm;
import com.example.studio_book.repository.ReviewRepository;
import com.example.studio_book.repository.RoomRepository;
import com.example.studio_book.security.UserDetailsImpl;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/rooms/{roomId}/reviews")
public class ReviewController {

  private final RoomRepository roomRepository;
  private final ReviewRepository reviewRepository;

  @GetMapping("/new")
  public String newForm(@PathVariable Integer roomId,
          @RequestParam(required=false) Integer reservationId,
          @RequestParam(defaultValue = "0") int page,   // ← ページ番号
          Model model) {
Room room = roomRepository.findById(roomId)
.orElseThrow(() -> new IllegalArgumentException("room not found"));

// ★ レビュー一覧（新しい順）と平均・件数
Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
var reviewsPage = reviewRepository.findByRoom_IdOrderByCreatedAtDesc(roomId, pageable);
Double avgScore = reviewRepository.getAverageScore(roomId);
long reviewCount = reviewRepository.countByRoomId(roomId);

model.addAttribute("room", room);
model.addAttribute("reservationId", reservationId);
model.addAttribute("reviewForm", new ReviewForm());

model.addAttribute("reviewsPage", reviewsPage);
model.addAttribute("avgScore", avgScore == null ? 0.0 : avgScore);
model.addAttribute("reviewCount", reviewCount);

return "reviews/new";
}

  @PostMapping
  @Transactional
  public String create(@PathVariable Integer roomId,
                       @Valid @ModelAttribute("reviewForm") ReviewForm form,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal UserDetailsImpl principal,
                       Model model) {

    if (bindingResult.hasErrors()) {
      model.addAttribute("room", roomRepository.findById(roomId).orElse(null));
      return "reviews/new";
    }

    Integer userId = principal.getUser().getId();

    // ★ここをプロパティパス版に変更
    boolean exists = reviewRepository.existsByRoom_IdAndUser_Id(roomId, userId);
    if (exists) {
      bindingResult.reject("duplicate", "このスタジオへのレビューは投稿済みです。");
      model.addAttribute("room", roomRepository.findById(roomId).orElse(null));
      return "reviews/new";
    }

    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("room not found"));

    // 永続化
    Review review = new Review();
    review.setRoom(room);
    review.setUser(principal.getUser());
    review.setScore(form.getScore());
    review.setContent(form.getContent());
    reviewRepository.save(review);

    return "redirect:/reservations";
  }
}
