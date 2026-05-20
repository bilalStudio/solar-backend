package com.wattvue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "solar_data")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolarData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    private Upload upload;

    @Column(nullable = false)
    private String month;

    // Used by date-range queries (system loss calc, before/after cleaning)
    @Column(name = "data_date")
    private LocalDate dataDate;

    @Column(name = "actual_kwh")
    private Double actualKwh;

    @Column(name = "estimated_kwh")
    private Double estimatedKwh;

    @Column(name = "variance_kwh")
    private Double varianceKwh;

    @Column(name = "variance_pct")
    private Double variancePct;

    @Column(name = "recorded_at")
    @Builder.Default
    private LocalDateTime recordedAt = LocalDateTime.now();
}
