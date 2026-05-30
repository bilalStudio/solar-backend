package com.wattvue.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "doc_photos")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"hibernateLazyInitializer","handler","fieldValues","photos"})
    private FieldDocument document;

    @Column(name = "field_key")
    private String fieldKey;

    @Column(name = "s3_url")
    private String s3Url;

    @Column(name = "file_name")
    private String fileName;

    private String caption;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
