// src/main/java/com/example/studio_book/form/BusinessHoursForm.java
package com.example.studio_book.form;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BusinessHoursForm {

    @NotNull
    private Integer roomId;

    @Valid
    @Builder.Default
    private List<BusinessHourRowForm> rows = new ArrayList<>(7);
}
