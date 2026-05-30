package com.wattvue.dto;

import lombok.Data;
import java.util.List;

@Data
public class FieldDocumentRequest {
    private String docType;
    private Long customerId;
    private String notes;
    private List<FieldValueDto> fieldValues;

    @Data
    public static class FieldValueDto {
        private String fieldKey;
        private String fieldLabel;
        private String fieldValue;
        private String fieldType;
        private Boolean isVisible;
        private Integer sortOrder;
    }
}
