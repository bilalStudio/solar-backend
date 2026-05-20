package com.wattvue.controller;

import com.wattvue.dto.ApiResponse;
import com.wattvue.model.Upload;
import com.wattvue.repository.UploadRepository;
import com.wattvue.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final FileUploadService fileUploadService;
    private final UploadRepository uploadRepository;

    /**
     * Upload any solar/cleaning data.
     * dataType values: SOLAR_ACTUAL, SOLAR_ESTIMATED, CLEANING_BEFORE, CLEANING_AFTER, SYSTEM
     * Columns for SOLAR_ACTUAL / SOLAR_ESTIMATED / CLEANING_*: Date | kWh
     * Columns for SYSTEM: Month/Date | Estimated kWh | Actual kWh
     */
    @PostMapping("/excel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "dataType", defaultValue = "SYSTEM") String dataType) {

        validateFile(file);
        Map<String, Object> result = fileUploadService.processUpload(file, customerId, dataType);
        return ResponseEntity.ok(ApiResponse.success("File uploaded and processed", result));
    }

    /**
     * Upload utility-meter data (15-min intervals, negative = export).
     * Columns: Timestamp | kWh
     */
    @PostMapping("/utility")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadUtility(
            @RequestParam("file") MultipartFile file,
            @RequestParam("customerId") Long customerId) {

        validateFile(file);
        Map<String, Object> result = fileUploadService.processUpload(file, customerId, "UTILITY");
        return ResponseEntity.ok(ApiResponse.success("Utility data uploaded and processed", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Upload>>> getUploads(@RequestParam Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Uploads retrieved",
                uploadRepository.findByCustomerIdOrderByUploadedAtDesc(customerId)));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("No file provided");
        }
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new RuntimeException("File has no name");
        }
        String lower = name.toLowerCase();
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xlsm") && !lower.endsWith(".csv")) {
            throw new RuntimeException("Only .xlsx, .xlsm, and .csv files are supported");
        }
    }
}