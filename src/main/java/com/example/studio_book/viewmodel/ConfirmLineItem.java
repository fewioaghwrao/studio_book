// src/main/java/com/example/studio_book/viewmodel/ConfirmLineItem.java
package com.example.studio_book.viewmodel;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConfirmLineItem {
  private String kind;           // base / flat_fee / multiplier / tax
  private String label;          // 画面表示用の説明
  private long amount;           // 円（整数）
  private LocalDateTime sliceStart; // 任意
  private LocalDateTime sliceEnd;   // 任意
}
