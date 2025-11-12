package com.example.studio_book.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserEditForm {
    @NotBlank(message = "氏名を入力してください。")
    private String name;

    @NotBlank(message = "フリガナを入力してください。")
    private String furigana;

    @NotBlank(message = "郵便番号を入力してください。")
    @Pattern(regexp = "\\d{7}", message = "郵便番号はハイフンなし7桁で入力してください。")
    private String postalCode;

    @NotBlank(message = "住所を入力してください。")
    private String address;

    @NotBlank(message = "電話番号を入力してください。")
    @Pattern(regexp = "\\d{10,11}", message = "電話番号はハイフンなし10〜11桁で入力してください。")
    private String phoneNumber;

    @NotBlank(message = "メールアドレスを入力してください。")
    @Email(message = "メール形式が不正です。")
    @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "メール形式が不正です。")
    private String email;
}