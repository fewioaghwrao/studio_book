// com.example.studio_book.entity.PriceRule
package com.example.studio_book.entity;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "price_rules")
@Data
public class PriceRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "room_id", nullable = false)
    private Integer roomId;

    @Column(name = "rule_type", nullable = false)
    private String ruleType; // "multiplier" or "flat_fee"

    @Column(name = "weekday")
    private Integer weekday; // 0-6, null = all

    @Column(name = "start_hour")
    private LocalTime startHour;

    @Column(name = "end_hour")
    private LocalTime endHour;

    @Column(name = "multiplier", precision = 6, scale = 2)
    private BigDecimal multiplier;

    @Column(name = "flat_fee")
    private Integer flatFee;

    @Column(name = "note")
    private String note;
}
