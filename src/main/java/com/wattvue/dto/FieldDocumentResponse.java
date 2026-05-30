package com.wattvue.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FieldDocumentResponse {
    private Long id;
    private String docType;
    private String docTypeLabel;
    private Long customerId;
    private String customerName;
    private Long technicianId;
    private String technicianName;
    private String status;
    private String pdfUrl;
    private String notes;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
    private List<FieldValueResponse> fieldValues;
    private List<PhotoResponse> photos;

    @Data
    @Builder
    public static class FieldValueResponse {
        private Long id;
        private String fieldKey;
        private String fieldLabel;
        private String fieldValue;
        private String fieldType;
        private Boolean isVisible;
        private Integer sortOrder;
    }

    @Data
    @Builder
    public static class PhotoResponse {
        private Long id;
        private String fieldKey;
        private String s3Url;
        private String fileName;
        private String caption;
    }
}
