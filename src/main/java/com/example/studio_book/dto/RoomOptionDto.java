// src/main/java/com/example/studio_book/dto/RoomOptionDto.java
package com.example.studio_book.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RoomOptionDto {
    private Integer id;
    private String name;
    private String hostName;
}
