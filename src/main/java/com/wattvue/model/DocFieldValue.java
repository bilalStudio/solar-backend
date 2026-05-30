package com.wattvue.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_field_values")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocFieldValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer","handler","fieldValues","photos"})
    private FieldDocument document;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "field_label")
    private String fieldLabel;

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;

    @Column(name = "field_type")
    private String fieldType = "TEXT";
    // TEXT | CHECKBOX | DATE | PHOTO | SIGNATURE | NUMBER

    @Column(name = "is_visible")
    private Boolean isVisible = true;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
