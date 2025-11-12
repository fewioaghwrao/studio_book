package com.example.studio_book.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "reservation_charge_items")
@Data
public class ReservationChargeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "reservation_id", nullable = false)
    private Integer reservationId;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "description")
    private String description;

    @Column(name = "slice_amount", nullable = false)
    private Integer sliceAmount;

    @Column(name = "slice_start")
    private LocalDateTime sliceStart;

    @Column(name = "slice_end")
    private LocalDateTime sliceEnd;

    @Column(name = "unit_rate_per_hour")
    private Integer unitRatePerHour;
}
