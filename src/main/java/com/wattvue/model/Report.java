package com.wattvue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "report_title")
    private String reportTitle;

    // PERFORMANCE | LOSS | CLEANING | UTILITY
    @Column(name = "report_type")
    @Builder.Default
    private String reportType = "PERFORMANCE";

    @Column(name = "file_path", length = 500)
    private String filePath;       // local path to generated PDF

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "email_status")
    @Builder.Default
    private String emailStatus = "PENDING";  // PENDING, SENT, FAILED

    @Column(name = "sent_to_email")
    private String sentToEmail;

    @Column(name = "generated_at")
    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
