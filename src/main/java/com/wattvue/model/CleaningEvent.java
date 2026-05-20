package com.wattvue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CLEANING EVENT
 * Records a panel-cleaning service and stores the before/after performance impact.
 */
@Entity
@Table(name = "cleaning_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleaningEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "cleaning_date", nullable = false)
    private LocalDate cleaningDate;

    @Column(name = "pre_avg_kwh")
    private Double preAvgKwh;

    @Column(name = "post_avg_kwh")
    private Double postAvgKwh;

    @Column(name = "kwh_gain")
    private Double kwhGain;

    @Column(name = "improvement_pct")
    private Double improvementPct;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
