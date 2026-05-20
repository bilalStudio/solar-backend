package com.wattvue.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class CleaningAnalysisRequest {

    @NotNull(message = "Customer ID is required")
    private Long customerId;

    @NotNull(message = "Cleaning date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate cleaningDate;

    // How many days before/after to compare (defaults to 14)
    private Integer windowDays;

    private String notes;
}
