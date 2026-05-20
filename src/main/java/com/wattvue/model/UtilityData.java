package com.wattvue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * UTILITY DATA ENTITY
 *
 * Stores 15-minute interval utility-meter readings.
 *
 * kWh values:
 *   POSITIVE  = energy IMPORTED from grid (consumption)
 *   NEGATIVE  = energy EXPORTED to grid (solar overproduction)
 *
 * Used for:
 *   - Validating that the solar system is exporting power
 *   - Comparing utility data vs actual system production
 */
@Entity
@Table(name = "utility_data", indexes = {
        @Index(name = "idx_utility_customer_ts", columnList = "customer_id, timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UtilityData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upload_id")
    private Upload upload;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "kwh", nullable = false)
    private Double kwh;

    @Column(name = "is_export")
    private Boolean isExport;   // true if kwh < 0
}
