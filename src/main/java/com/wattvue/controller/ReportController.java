package com.wattvue.controller;

import com.wattvue.dto.ApiResponse;
import com.wattvue.dto.CleaningAnalysisRequest;
import com.wattvue.dto.EmailReportRequest;
import com.wattvue.dto.LossCalculationRequest;
import com.wattvue.model.Customer;
import com.wattvue.model.Report;
import com.wattvue.repository.CustomerRepository;
import com.wattvue.repository.ReportRepository;
import com.wattvue.service.AnalyticsService;
import com.wattvue.service.ExcelExportService;
import com.wattvue.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportRepository reportRepository;
    private final CustomerRepository customerRepository;
    private final ExcelExportService excelExportService;
    private final AnalyticsService analyticsService;

    @PostMapping("/generate/{customerId}")
    public ResponseEntity<ApiResponse<Report>> generatePerformance(@PathVariable Long customerId) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("Performance report generated",
                reportService.generatePerformanceReport(customerId)));
    }

    @PostMapping("/generate-loss")
    public ResponseEntity<ApiResponse<Report>> generateLoss(@Valid @RequestBody LossCalculationRequest req) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("Loss report generated",
                reportService.generateLossReport(req)));
    }

    @PostMapping("/generate-cleaning")
    public ResponseEntity<ApiResponse<Report>> generateCleaning(@Valid @RequestBody CleaningAnalysisRequest req) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("Cleaning report generated",
                reportService.generateCleaningReport(req)));
    }

    @PostMapping("/generate-utility/{customerId}")
    public ResponseEntity<ApiResponse<Report>> generateUtility(@PathVariable Long customerId) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("Utility report generated",
                reportService.generateUtilityReport(customerId)));
    }

    @GetMapping("/history/{customerId}")
    public ResponseEntity<ApiResponse<List<Report>>> getHistory(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Report history",
                reportRepository.findByCustomerIdOrderByGeneratedAtDesc(customerId)));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Report>>> getAllReports() {
        return ResponseEntity.ok(ApiResponse.success("All reports",
                reportRepository.findAllByOrderByGeneratedAtDesc()));
    }

    @PostMapping("/email/{reportId}")
    public ResponseEntity<ApiResponse<Report>> sendEmail(@PathVariable Long reportId,
                                                         @Valid @RequestBody EmailReportRequest request) throws Exception {
        return ResponseEntity.ok(ApiResponse.success("Email sent",
                reportService.sendReportEmail(reportId, request)));
    }

    @GetMapping("/download/{reportId}")
    public ResponseEntity<FileSystemResource> downloadReport(@PathVariable Long reportId) {
        File file = reportService.getReportFile(reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.length())
                .body(new FileSystemResource(file));
    }

    @GetMapping("/excel/{customerId}")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Long customerId) throws Exception {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        Map<String, Object> roi = analyticsService.getROIAnalysis(customerId);
        byte[] bytes = excelExportService.exportPerformanceData(customer, roi);

        String filename = customer.getName().replaceAll("[^a-zA-Z0-9]", "_") + "_performance.xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @DeleteMapping("/{reportId}")
    public ResponseEntity<ApiResponse<Void>> deleteReport(@PathVariable Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        try {
            File f = new File(report.getFilePath());
            if (f.exists()) f.delete();
        } catch (Exception ignored) {}

        reportRepository.delete(report);
        return ResponseEntity.ok(ApiResponse.success("Report deleted", null));
    }
}
