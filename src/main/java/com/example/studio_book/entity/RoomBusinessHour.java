// src/main/java/com/example/studio_book/entity/RoomBusinessHour.java
package com.example.studio_book.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "room_businesshours",
       uniqueConstraints = @UniqueConstraint(name = "uq_room_day", columnNames = {"room_id", "day_index"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RoomBusinessHour {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name="fk_room_businesshours_room"))
    private Room room;

    /** 1=Mon ... 7=Sun */
    @Column(name = "day_index", nullable = false)
    private Integer dayIndex;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "holiday", nullable = false)
    private boolean holiday;
}
