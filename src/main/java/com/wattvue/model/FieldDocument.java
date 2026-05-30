package com.wattvue.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "field_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doc_type", nullable = false)
    private String docType;
    // HEALTH_CHECKLIST | SITE_SURVEY | INSTALL_SERVICE | CREW_UPDATE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private FieldUser technician;

    @Column(nullable = false)
    private String status = "DRAFT";
    // DRAFT | SUBMITTED | PDF_GENERATED | EMAILED

    @Column(name = "pdf_url")
    private String pdfUrl;

    private String notes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private List<DocFieldValue> fieldValues;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private List<DocPhoto> photos;
}
