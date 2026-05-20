package com.wattvue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "uploads")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Upload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "s3_key")
    private String s3Key;

    // SYSTEM (solar production data) or UTILITY (utility meter data)
    @Column(name = "data_type")
    @Builder.Default
    private String dataType = "SYSTEM";

    @Column(name = "status")
    @Builder.Default
    private String status = "PROCESSING";

    @Column(name = "rows_processed")
    private Integer rowsProcessed;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "uploaded_at")
    @Builder.Default
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;
}
